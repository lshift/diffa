package net.lshift.diffa.agent.rest

import scala.collection.JavaConversions._
import javax.ws.rs.{Produces, Path, GET}
import javax.ws.rs.core.{StreamingOutput, Response, Context}
import javax.servlet.http.HttpServletRequest
import net.lshift.diffa.adapter.scanning.{SliceSizeParser, ScanResultEntry, AggregationBuilder, ConstraintsBuilder}
import org.springframework.stereotype.Component
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.beans.factory.annotation.Autowired
import net.lshift.diffa.kernel.differencing.DomainDifferenceStore
import java.io.{BufferedOutputStream, OutputStream}
import org.apache.avro.io.EncoderFactory
import org.apache.avro.specific.SpecificDatumWriter
import net.lshift.diffa.scanning.PruningHandler
import net.lshift.diffa.interview.{GroupedAnswer, IndividualAnswer, Answer, SerializableGroupedAnswer}


@Path("/store")
@Component
@PreAuthorize("hasRole('root')")
class StoreSynchronizationResource {

  @Autowired var diffStore:DomainDifferenceStore = null

  @GET
  @Path("/scan")
  @Produces(Array("application/json"))
  def scanPairs(@Context request:HttpServletRequest) = {

    val constraintsBuilder = new ConstraintsBuilder(request)
    constraintsBuilder.maybeAddStringPrefixConstraint("name")
    val constraints = constraintsBuilder.toSet

    val aggregationsBuilder = new AggregationBuilder(request)
    aggregationsBuilder.maybeAddStringPrefixAggregation("entityId")
    val aggregations = aggregationsBuilder.toSet

    val sliceParser = new SliceSizeParser(request)
    val maxSliceSize = sliceParser.getMaxSliceSize

    val stream = new StreamingOutput {
      def write(os: OutputStream) {

        val encoder = EncoderFactory.get().binaryEncoder(os, null)

        // We're using this indirection (GeneratedScanResultEntry) at the moment so as to not put a direct dependency on avro
        // in the kernel module - should this code ever stabilize over time, we can think about getting rid of the
        // indirection

        val writer = new SpecificDatumWriter[SerializableGroupedAnswer](classOf[SerializableGroupedAnswer]);
        writer.setSchema(SerializableGroupedAnswer.SCHEMA$);

        val handler = new PruningHandler {
          def onPrune(answer: Answer) = answer match {
            case i:IndividualAnswer => throw new RuntimeException("Need to implement code to handle individual case: " + i)
            case group:GroupedAnswer    =>
              val builder = SerializableGroupedAnswer.newBuilder()

              builder.setDigest(group.getDigest)
              builder.setGroup(group.getGroup)

              writer.write(builder.build(), encoder)
              encoder.flush()
          }

          def onCompletion() {}
        }

        diffStore.scan(constraints, aggregations, maxSliceSize, handler)

      }
    }

    Response.ok(stream).build()

  }

}

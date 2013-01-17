package net.lshift.diffa.scanning.http;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.ning.http.client.*;
import net.lshift.diffa.adapter.scanning.ScanAggregation;
import net.lshift.diffa.adapter.scanning.ScanConstraint;
import net.lshift.diffa.interview.Answer;
import net.lshift.diffa.interview.SerializableGroupedAnswer;
import net.lshift.diffa.interview.SimpleGroupedAnswer;
import net.lshift.diffa.scanning.PruningHandler;
import net.lshift.diffa.scanning.Scannable;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.Decoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.specific.SpecificDatumReader;
import org.joda.time.DateTime;

import java.io.EOFException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class HttpDriver implements Scannable {

  private AsyncHttpClient client = new AsyncHttpClient();
  private String url;
  private Realm realm;

  @Inject
  public HttpDriver(@Named("scan.url") String url) {
    this.url = url;
  }
  /*
  @Inject
  public HttpDriver(String url, String user, String password) {
    this.url = url;
    this.realm = new Realm.RealmBuilder()
                          .setPrincipal(user)
                          .setPassword(password)
                          .setUsePreemptiveAuth(true)
                          .setScheme(Realm.AuthScheme.BASIC)
                          .build();
  }
  */

  @Override
  public void scan(Set<ScanConstraint> constraints, Set<ScanAggregation> aggregations, int maxSliceSize, final PruningHandler pruningHandler) {

    FluentStringsMap queryParams = ScanRequestEncoder.packRequest(constraints, aggregations, maxSliceSize);

    Request req =
      new RequestBuilder("GET").setUrl(url).
                                setQueryParameters(queryParams).
                                build();

    final DatumReader<SerializableGroupedAnswer> reader = new SpecificDatumReader<SerializableGroupedAnswer>(SerializableGroupedAnswer.class);

    AsyncHandler<Response> asyncHandler = new AsyncHandler<Response>() {

      private final Response.ResponseBuilder builder = new Response.ResponseBuilder();

      @Override
      public void onThrowable(Throwable t) {
        throw new RuntimeException(t);
      }

      @Override
      public STATE onBodyPartReceived(HttpResponseBodyPart bodyPart) throws Exception {

        Decoder decoder = DecoderFactory.get().binaryDecoder(bodyPart.getBodyPartBytes(), null);

        while (true) {
          try {

            SerializableGroupedAnswer answer = reader.read(null, decoder);
            pruningHandler.onPrune(new SimpleGroupedAnswer(answer));


          } catch (EOFException e) {
            break;
          }
        }

        pruningHandler.onCompletion();


        return STATE.CONTINUE;
      }

      @Override
      public STATE onStatusReceived(HttpResponseStatus responseStatus) throws Exception {
        builder.accumulate(responseStatus);
        return STATE.CONTINUE;
      }

      @Override
      public STATE onHeadersReceived(HttpResponseHeaders headers) throws Exception {
        builder.accumulate(headers);
        return STATE.CONTINUE;
      }

      @Override
      public Response onCompleted() throws Exception {
        return builder.build();
      }
    };

    try {

      AsyncHttpClient.BoundRequestBuilder builder = client.prepareRequest(req);

      if (realm != null) {
        builder = builder.setRealm(realm);
      }

      builder.execute(asyncHandler).get();

    } catch (Exception e) {
      throw new RuntimeException(e);
    }

  }

}

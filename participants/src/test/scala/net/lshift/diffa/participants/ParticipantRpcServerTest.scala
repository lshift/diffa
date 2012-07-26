package net.lshift.diffa.participants

import org.junit.Test
import org.junit.Assert._
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.client.methods.HttpGet
import org.apache.http.auth.{UsernamePasswordCredentials, AuthScope}

class ParticipantRpcServerTest {

  val port = 30000

  // ATM we have not wired the correct scan protocol into this test, so we are going
  // to have to deliberately use a bogus path
  val url = "http://localhost:%s/bogus".format(port)

  val username = "scott"
  val password = "tiger"
  val basicAuth = BasicAuthenticationMechanism(Map(username -> password))

  val queryParameter = "authToken"
  val queryParameterValue = "23c759bb46577a"
  val queryParameterAuth = QueryParameterAuthenticationMechanism(queryParameter, queryParameterValue)

  @Test
  def serverShouldBeConfigurableWithNoAuthentication() = {

    val server = new ParticipantRpcServer(port, null, null, null, NoAuthentication)
    server.start

    val httpClient = new DefaultHttpClient
    submitAndVerifyResponse(httpClient, url, 404)

    httpClient.getConnectionManager.shutdown()
    server.stop
  }

  @Test
  def serverShouldSupportBasicAuthentication() = {

    val server = new ParticipantRpcServer(port, null, null, null, basicAuth)
    server.start

    val httpClient = new DefaultHttpClient
    httpClient.getCredentialsProvider.setCredentials(
      new AuthScope("localhost", port),
      new UsernamePasswordCredentials(username, password)
    )

    submitAndVerifyResponse(httpClient, url, 404)
    httpClient.getConnectionManager.shutdown()

    server.stop
  }

  @Test
  def serverShouldRejectBadCredentialsWithBasicAuthentication() = {

    val server = new ParticipantRpcServer(port, null, null, null, basicAuth)
    server.start

    val httpClient = new DefaultHttpClient
    httpClient.getCredentialsProvider.setCredentials(
      new AuthScope("localhost", port),
      new UsernamePasswordCredentials(username, password + "bogus")
    )

    submitAndVerifyResponse(httpClient, url, 401)
    httpClient.getConnectionManager.shutdown()

    server.stop
  }

  @Test
  def serverShouldSupportQueryParameterAuthentication() = {

    val query = url + "?" + queryParameter + "=" + queryParameterValue

    val server = new ParticipantRpcServer(port, null, null, null, queryParameterAuth)
    server.start

    val httpClient = new DefaultHttpClient

    submitAndVerifyResponse(httpClient, query, 404)
    httpClient.getConnectionManager.shutdown()

    server.stop
  }

  @Test
  def serverShouldRejectBadCredentialsWithQueryParameterAuthentication() = {

    val query = url + "?" + queryParameter + "=" + queryParameterValue + "bogus"

    val server = new ParticipantRpcServer(port, null, null, null, queryParameterAuth)
    server.start

    val httpClient = new DefaultHttpClient

    submitAndVerifyResponse(httpClient, query, 403)
    httpClient.getConnectionManager.shutdown()

    server.stop
  }



  private def submitAndVerifyResponse(httpClient:DefaultHttpClient,url:String, expectation:Int) = {
    val httpGet = new HttpGet(url)
    val response = httpClient.execute(httpGet)
    val statusCode = response.getStatusLine.getStatusCode
    assertEquals(expectation, statusCode)
  }
}

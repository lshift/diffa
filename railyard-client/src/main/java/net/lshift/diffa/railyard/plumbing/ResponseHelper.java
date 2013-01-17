package net.lshift.diffa.railyard.plumbing;

import com.ning.http.client.Response;
import net.lshift.diffa.railyard.RailYardException;

public class ResponseHelper {

  public static void verifyResponse(Response response, int code) {
    if (response.getStatusCode() != code) {
      switch (response.getStatusCode()) {
        default: throw new RailYardException("HTTP " + response.getStatusCode() + " : " + response.getStatusText());
      }
    }
  }

}

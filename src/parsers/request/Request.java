package parsers.request;

public class Request {

  private final String req;

  public Request(String req) {
    this.req = req;
  }

  @Override
  public String toString() {
    return req;
  }

}

package parsers.request;

import java.util.List;

public abstract class TokenizedRequest extends Request {

  private final List<String> tokens;
  private final RequestType type;

  protected TokenizedRequest(String req, List<String> tokens, RequestType type) {
    super(req);
    this.tokens = tokens;
    this.type = type;
  }

  public RequestType type() {
    return type;
  }

  protected List<String> tokens() {
    return tokens;
  }

  public enum RequestType {
    QUIT,
    STAT,
    COMP;
  }

}

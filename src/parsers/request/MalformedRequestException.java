package parsers.request;

public class MalformedRequestException extends Exception{
  public MalformedRequestException(String message) {
    super(message);
  }
}
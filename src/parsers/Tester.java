package parsers;

import parsers.request.MalformedRequestException;
import parsers.request.Request;
import parsers.request.RequestParser;
import parsers.request.TokenizedRequest;

public class Tester {
  public static void main(String[] args) throws MalformedRequestException {
    String s = "MAX_GRID;x0:-1:0.1:1,x1:-10:1:20;((x0+(2.0^x1))/(1-x0))";
  }
}

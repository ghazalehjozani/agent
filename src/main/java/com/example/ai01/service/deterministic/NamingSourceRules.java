package com.example.ai01.service.deterministic;
import com.example.ai01.agent.model.Field;
import java.util.*;
import java.util.regex.*;
final class NamingSourceRules {
 private NamingSourceRules(){}
 static boolean isSerializableField(String source,Field field){String n=Pattern.quote(field.name());Pattern declaration=Pattern.compile("(?m)^[^\\n;]*\\b"+n+"\\b[^;]*;");Matcher m=declaration.matcher(source==null?"":source);if(!m.find())return true;String d=m.group();return !d.matches("(?s).*\\bstatic\\b.*")&&!d.matches("(?s).*\\btransient\\b.*")&&!d.contains("@JsonIgnore");}
 static List<HeaderValue> authorizationHeaders(String path,String source){List<HeaderValue>o=new ArrayList<>();Pattern p=Pattern.compile("(?:@RequestHeader\\s*\\(\\s*(?:(?:value|name)\\s*=\\s*)?|(?:getHeader|header|add|set)\\s*\\(\\s*)[\"']([^\"']+)[\"']",Pattern.CASE_INSENSITIVE);Matcher m=p.matcher(source==null?"":source);while(m.find()){String v=m.group(1);if(v.equalsIgnoreCase("authorization"))o.add(new HeaderValue(path,v,line(source,m.start())));}return List.copyOf(o);} private static int line(String s,int o){int n=1;for(int i=0;i<o;i++)if(s.charAt(i)=='\n')n++;return n;} record HeaderValue(String file,String value,int line){}
}

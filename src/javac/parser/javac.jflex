package javac.parser;
import java_cup.runtime.*;
%%

%unicode
%line
%column
%cup
%implements Symbols

%{
  StringBuffer str = new StringBuffer();
  private int commentCount = 0;

  private Symbol tok(int type) {
	  return new Symbol(type, yyline, yycolumn);
  }
  private Symbol tok(int type, Object value) {
	  return new Symbol(type, yyline, yycolumn, value);
  }
  private void err(String message) {
        System.err.println(String.format("Scanning error in line %d, column %d: %s", yyline + 1, yycolumn + 1, message));
  }

%}

%eofval{
	{
		if(yystate() != YYINITIAL && yystate() != YYLINECOMMENT){
			err("unexpected EOF.");
		}
		return tok(EOF,null);
	}
%eofval}

LineTerm = \n|\r|\r\n
Identifier = [_a-zA-Z][_a-zA-Z0-9]*
DecInteger = [0-9]+
Whitespace = {LineTerm}|[ \t\f]
%state YYSTRING
%state YYCHAR
%state YYCOMMENT
%state YYLINECOMMENT

%%
<YYINITIAL> {
  "//"                 { yybegin(YYLINECOMMENT); }
  "/*"			{commentCount=1;yybegin(YYCOMMENT);}
  "["{Whitespace}*"]"  { return tok(LRBRACKET); }
  \"			{str.setLength(0);yybegin(YYSTRING);}
  \'			{str.setLength(0);yybegin(YYCHAR);}
  "native"		{return tok(NATIVE);}
  "record"		{return tok(RECORD);}
  "new"			{return tok(NEW);}
  "int"			{return tok(INT);}
  "string"		{return tok(STRING);}
  "char"		{return tok(CHAR);}
  "null"		{return tok(NULL);}
  "if"			{return tok(IF);}
  "else"		{return tok(ELSE);}
  "while"		{return tok(WHILE);}
  "for"			{return tok(FOR);}
  "return"		{return tok(RETURN);}
  "break"		{return tok(BREAK);}
  "continue"		{return tok(CONTINUE);}
  ";"			{return tok(SEMICOLON);}
  "["			{return tok(LBRACKET);}
  "]"			{return tok(RBRACKET);}
  "{"			{return tok(LBRACE);}
  "}"			{return tok(RBRACE);}
  "("			{return tok(LPAREN);}
  ")"			{return tok(RPAREN);}
  ","			{return tok(COMMA);}
  "="			{return tok(ASSIGN);}
  "||"			{return tok(OR);}
  "&&"			{return tok(AND);}
  "=="			{return tok(EQ);}
  "!="			{return tok(NEQ);}
  "<"			{return tok(LESS);}
  "<="			{return tok(LESS_EQ);}
  ">"			{return tok(GREATER);}
  ">="			{return tok(GREATER_EQ);}
  "+"			{return tok(PLUS);}
  "-"			{return tok(MINUS);}
  "*"			{return tok(MULTIPLY);}
  "/"			{return tok(DIVIDE);}
  "%"			{return tok(MODULO);}
  "!"			{return tok(NOT);}
  "."			{return tok(DOT);}
  
  {Identifier}  { return tok(ID, yytext()); }
  {DecInteger}  { return tok(INTEGER, Integer.valueOf(yytext())); }
  {Whitespace}  {}
  
  [^]  { err("Illegal character " + yytext()); }
}

<YYCHAR> {
	\'	{
		if (str.length() !=1 ) {
			err("Invalid char");
		} else {
			yybegin(YYINITIAL);
			return tok(CHARACTER, str.charAt(0));
		}
	}
	
	\\t			{str.append('\t'); }
	\\r 			{str.append('\r'); }
	\\n 			{str.append('\n'); }
	\\\'			{str.append('\''); }
	\\\\			{str.append('\\'); }
	\\[0-9][0-9][0-9]	{
		int z = Integer.valueOf(yytext().substring(1, 4));
		if (z > 255) {
			err("Invalid char");
		} else {
			str.append((char) z);
		}
	}
	[^\r\n\t\'\\]+		{str.append(yytext()); }
	{LineTerm}		{err("unexpected EOL");}
}

<YYSTRING> {
	\"	{
		yybegin(YYINITIAL);
		return tok(STRING_LITERAL,str.toString());
	}
	\\t			{str.append('\t'); }
	\\r 			{str.append('\r'); }
	\\n 			{str.append('\n'); }
	\\\"			{str.append('\"'); }
	\\\\			{str.append('\\'); }
	\\[0-9][0-9][0-9]	{
		int z = Integer.valueOf(yytext().substring(1, 4));
		if (z > 255) {
			err("Invalid char");
		} else {
			str.append((char) z);
		}
	}
	[^\r\n\t\"\\]+		{str.append(yytext()); }
	{LineTerm}		{err("unexpected EOL");}
}



<YYLINECOMMENT> {
  {LineTerm}  { yybegin(YYINITIAL); }
  [^]  {}
}

<YYCOMMENT> {
  /* TODO: implement nested comment mechanism */
	"/*"	{commentCount++;}
	"*/"	{commentCount--;if(commentCount==0)yybegin(YYINITIAL);}
	[^]	{}
}

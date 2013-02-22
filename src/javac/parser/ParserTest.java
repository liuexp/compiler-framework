package javac.parser;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.File;

import javac.absyn.TranslationUnit;
import javac.util.AbsynFormatter;

public final class ParserTest {

	public static void main(String[] args) throws Exception {
		/*File dir= new File("./compiler-testcases/syntactic/good/");
		for(File f : dir.listFiles()){
			if(".".equals(f.getName())||"..".equals(f.getName())||!f.getName().contains(".java"))continue;
			System.out.println(f.getName()+":\n");
			parse(f.getAbsolutePath());
		}*/
		//parse("queens.java");
		//parse("testcase/killer2.java");
		parse("./compiler-testcases/std/Board.java");
	}
	
	private static void parse(String filename) {
		try {
			final InputStream in = new FileInputStream(filename);
			final Parser parser = new Parser(new Yylex(in));
			final java_cup.runtime.Symbol parseTree = parser.parse();
			in.close();
			final TranslationUnit translationUnit = (TranslationUnit) parseTree.value;
			System.out.println(AbsynFormatter.format(translationUnit.toString()));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}

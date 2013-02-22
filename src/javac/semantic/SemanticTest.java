package javac.semantic;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import javac.absyn.TranslationUnit;
import javac.env.Env;
import javac.parser.Parser;
import javac.util.AbsynFormatter;
import java_cup.runtime.*;

public class SemanticTest {

	public static void main(String[] args) throws Exception {
		/*File dir= new File("./compiler-testcases/std/");
		for(File f : dir.listFiles()){
			if(".".equals(f.getName())||"..".equals(f.getName())||!f.getName().contains(".java"))continue;
			test(f.getAbsolutePath());
		}*/
		/*File dir= new File("./testcase/good/");
		for(File f : dir.listFiles()){
			if(".".equals(f.getName())||"..".equals(f.getName())||!f.getName().contains(".java"))continue;
			test(f.getAbsolutePath());
		}*/
		//test("testcase/good/func0.java");
		//test("queens.java");
		//test("testcase/zzz.java");
		//test("zz.java");
		//test("./compiler-testcases/semantic/good/strings.java");
		//test("./compiler-testcases/std/Horse3.java");
	}
	
	private static Env init()  {
		Env ret=new Env();
		try {
			final InputStream in = new FileInputStream("contrib.eee");
			final Parser parser = new Parser(in);
			final Symbol parseTree = parser.parse();
			in.close();
			final TranslationUnit translationUnit = (TranslationUnit) parseTree.value;
			//TODO: parse twice?
			GlobalVisitor v1 = new GlobalVisitor(new Env());
			translationUnit.accept(v1);
			RecordVisitor v2 = new RecordVisitor(v1.getEnv());
			translationUnit.accept(v2);
			FuncVisitor v3 = new FuncVisitor(v2.getEnv());
			translationUnit.accept(v3);
			ret= v3.getEnv();
		} catch (SException e) {
			e.printStackTrace();
			System.exit(1);
		} catch(Exception e){
			e.printStackTrace();
			System.exit(1);
		}
		return ret;
	}

	private static void test(String f) throws Exception {
		try {
			System.out.println("testing:"+f+":\n");
			final InputStream in = new FileInputStream(f);
			final Parser parser = new Parser(in);
			final Symbol parseTree = parser.parse();
			in.close();
			final TranslationUnit translationUnit = (TranslationUnit) parseTree.value;
			
			GlobalVisitor v1 = new GlobalVisitor(init());
			translationUnit.accept(v1);
			RecordVisitor v2 = new RecordVisitor(v1.getEnv());
			translationUnit.accept(v2);
			FuncVisitor v3 = new FuncVisitor(v2.getEnv());
			translationUnit.accept(v3);
			System.out.println(AbsynFormatter.format(translationUnit.toString()));
		} catch (SException e) {
			e.printStackTrace();
			//System.exit(1);
		} catch(Exception e){
			e.printStackTrace();
			//System.exit(1);
		}
	}

}

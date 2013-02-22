package javac.trans;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.HashSet;
import java.util.LinkedList;

import java_cup.runtime.Symbol;
import javac.absyn.TranslationUnit;
import javac.block.LiveAnalysis;
import javac.env.Env;
import javac.parser.Parser;
import javac.semantic.FuncVisitor;
import javac.semantic.GlobalVisitor;
import javac.semantic.RecordVisitor;
import javac.semantic.SException;
import javac.util.AbsynFormatter;

public class TransTest {

	public static LinkedList<Frags> IR;
	public static void main(String[] args) throws Exception {
		/*File dir= new File("./compiler-testcases/std/");
		for(File f : dir.listFiles()){
			if(".".equals(f.getName())||"..".equals(f.getName())||!f.getName().contains(".java"))continue;
			translate(f.getAbsolutePath());
		}*/
		//translate("./compiler-testcases/semantic/good/strings.java");
		//translate("testcase/zzzz.java");
		translate("./compiler-testcases/std/Board.java");
		//translate("./zz.java");
	}

	private static Env init()  {
		Env ret=new Env();
		try {
			final InputStream in = new FileInputStream("contrib.eee");
			final Parser parser = new Parser(in);
			final Symbol parseTree = parser.parse();
			in.close();
			final TranslationUnit translationUnit = (TranslationUnit) parseTree.value;
			GlobalVisitor v1 = new GlobalVisitor(new Env());
			translationUnit.accept(v1);
			RecordVisitor v2 = new RecordVisitor(v1.getEnv());
			translationUnit.accept(v2);
			FuncVisitor v3 = new FuncVisitor(v2.getEnv());
			translationUnit.accept(v3);
			ret= v3.getEnv();
			IR=Trans.trans(translationUnit);
		} catch (SException e) {
			e.printStackTrace();
			System.exit(1);
		} catch(Exception e){
			e.printStackTrace();
			System.exit(1);
		}
		return ret;
	}
	
	private static void translate(String f) throws Exception {
		try {
			System.out.println("testing:"+f+":\n");
			final InputStream in = new FileInputStream(f);
			RandomAccessFile raf=new RandomAccessFile(f, "r");
			//Trans.inlineEnabled=false;
			if(raf.length()>9531){//TODO:if too many temps inside a block also turn it off.
				LiveAnalysis.enabled=false;
				Trans.inlineEnabled=false;
			}
			if(raf.length()>20331){//TODO:if too many temps inside a block also turn it off.
				LiveAnalysis.semiEnabled=false;
				//System.out.println("[Warning]Big files detected, turning off optimizations.\n\t" +"You can manually turn it back on by explicitly specifying -O");
			}
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
			//System.out.println(AbsynFormatter.format(translationUnit.toString()));
			//IR.addAll(Trans.trans(translationUnit));
			//LiveAnalysis.semiEnabled=LiveAnalysis.enabled=false;
			IR=Trans.trans(translationUnit);
			for (Frags frag: IR) {
				//System.out.println(frag.toAsm());
				System.out.println(frag.toString());
			}
			
		} catch (SException e) {
			e.printStackTrace();
			//System.exit(1);
		} catch(Exception e){
			e.printStackTrace();
			//System.exit(1);
		}
		
	}

}


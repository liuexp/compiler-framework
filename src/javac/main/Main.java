package javac.main;

import java.io.*;
import java.util.LinkedList;

import java_cup.runtime.Symbol;
import javac.absyn.TranslationUnit;
import javac.block.LiveAnalysis;
import javac.block.ReachingDefinition;
import javac.env.Env;
import javac.parser.Parser;
import javac.peephole.Peephole;
import javac.semantic.FuncVisitor;
import javac.semantic.GlobalVisitor;
import javac.semantic.RecordVisitor;
import javac.semantic.SException;
import javac.trans.Frags;
import javac.trans.Trans;
import javac.util.AbsynFormatter;
import org.apache.commons.io.FileUtils;

public class Main {
	public static LinkedList<Frags> IR;
	private static Env init()  {
		Env ret=new Env();
		try {
			final InputStream in2 = new BufferedInputStream(Main.class.getResourceAsStream("contrib.eee"));
			
			final Parser parser = new Parser(in2);
			final Symbol parseTree = parser.parse();
			in2.close();
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
	public static void main(String[] args) throws Exception {
		if(args.length <1){
			System.out.println("Usage: java main.jar infile <outfile>");
			System.exit(0);
		}
		try {
			String f = args[0];
			String outfile = null;
			boolean parseOnly=false,irOnly=false,O1=false,O2=false;
			if(args.length>=2){
				if(args.length>=3){
					if(args[args.length-2].equals("-ofile")){
						outfile=args[args.length-1];
						f=args[args.length-3];
					}
				}
				else f=args[args.length-1];
				for (String a:args){
					if(a.equals("-parseOnly"))parseOnly=true;
					if(a.equals("-irOnly"))irOnly=true;
					if(a.equals("-O2"))O1=O2=true;
					if(a.equals("-O1"))O1=true;
				}
			}
			RandomAccessFile raf=new RandomAccessFile(f, "r");
			if(raf.length()>9531){//TODO:if too many temps inside a block also turn it off.
				LiveAnalysis.enabled=false;
				Trans.inlineEnabled=false;
				ReachingDefinition.enabled=false;
			}
			if(raf.length()>20331){//TODO:if too many temps inside a block also turn it off.
				LiveAnalysis.semiEnabled=false;
				//System.out.println("[Warning]Big files detected, turning off optimizations.\n\t" +"You can manually turn it back on by explicitly specifying -O");
			}
			//Trans.inlineEnabled=false;
			raf.close();
			raf=null;
			if(O1){
				LiveAnalysis.semiEnabled=LiveAnalysis.enabled=true;
			}
			if(outfile==null)outfile=f.substring(0, f.length()-5)+".s";
			System.out.println("Parsing & Analyzing : "+f);
			final InputStream in = new FileInputStream(f);
			FileUtils.copyInputStreamToFile(Main.class.getResourceAsStream("native.eee"),new File(outfile));
			final StringBuffer result = new StringBuffer();
			//final Writer out = new OutputStreamWriter(FileUtils.openOutputStream(new File(outfile),true));
			//final Writer out = new OutputStreamWriter(new FileOutputStream(outfile));
			//final String runtime = FileUtils.readFileToString(FileUtils.toFile(Main.class.getResource("native.eee")));
			final Parser parser = new Parser(in);
			final Symbol parseTree = parser.parse();
			
			final TranslationUnit translationUnit = (TranslationUnit) parseTree.value;
			GlobalVisitor v1 = new GlobalVisitor(init());
			translationUnit.accept(v1);
			RecordVisitor v2 = new RecordVisitor(v1.getEnv());
			translationUnit.accept(v2);
			FuncVisitor v3 = new FuncVisitor(v2.getEnv());
			translationUnit.accept(v3);
			in.close();
			
			if(parseOnly){
				System.out.println(AbsynFormatter.format(translationUnit.toString()));
				System.exit(0);
			}
			System.out.println("Translating into IR: "+f);
			IR.addAll(Trans.trans(translationUnit));
			//out.write(runtime);
			//out.write("\n");
			System.out.println("Generating Codes: "+f);
			result.append("\n");
			if(irOnly){
				for (Frags frag: IR) {
					System.out.println(frag.toString());
				}
				System.exit(0);
			}
			for (Frags frag: IR) {
				//out.write(frag.toAsm()+"\n");
				result.append(frag.toAsm()+"\n");
			}
			//out.flush();
			//out.close();
			//String ans=Peephole.peephole(result.toString());
			String ans=result.toString();
			FileUtils.writeStringToFile(new File(outfile), result.toString(), true);
			System.out.println("[Succeeded]");
			System.exit(0);
		} catch (SException e) {
			System.out.println("[Failed]: "+e.getMessage());
			//e.printStackTrace();
			System.exit(1);
		}  catch (Exception e) {
			System.out.println("[Failed]: "+e.getMessage());
			e.printStackTrace();
			System.exit(1);
		}
	}
}

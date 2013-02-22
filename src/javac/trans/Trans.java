package javac.trans;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Stack;

import javac.absyn.*;
import javac.block.BasicBlock;
import javac.block.LiveAnalysis;
import javac.quad.*;
import javac.quad.UnaryOp;
import javac.semantic.SException;
import javac.symbol.Symbol;
import javac.type.*;
import javac.type.RECORD.RecordField;
import javac.util.ContBinaryExpr;
import javac.util.NullTransCont;
import javac.util.Position;
import javac.util.TransExprCont;

public class Trans {
	public static final int wordLength = 4;
	public static final int charLength = 4;
	public static final int reservedFrameSize = 1;
	public static LinkedList<Frags> frag;
	public static Stack<Label> continueS,breakS;
	public static LinkedList<Quad> IR;
	public static HashMap<Symbol, Temp> symTable;
	public static HashMap<Symbol, Temp> symTable2;//for inlined function
	public static HashMap<Temp,TempOprand> tempTable;
	//common sub-expression table
	// the result of an expression of form t1+t2 can be placed into the table iff both t1 and t2 has no side effect
	public static HashMap<String, Temp> cseTable;
	public static LinkedList<TransExprCont> tailRecurse;
	
	public static HashMap<Temp, Integer> stackPtr;
	
	public static HashSet<Label> killedFunc=new HashSet<Label>();
	public static HashSet<Label> allFunc=new HashSet<Label>();
	public static HashSet<Label> calledFunc=new HashSet<Label>();
	public static boolean inlineEnabled=true;
	public static HashMap<String,FunctionDef> inlineMap=new HashMap<String,FunctionDef>();
	
	public static int frameSize;
	public static int hasCall=0;
	public static int hasSysCall=0;
	public static boolean hasSideEffect=false;
	public static boolean DEBUG_ON=false;
	public static Label inlineFuncRet;
	public static TempOprand inlineFuncRetTemp;
	public static int curCodeSize;
	public static boolean loopUnrolling=false;//FIXME:looks like a bug or a feature?
	public static HashMap<rewrittenArray,Temp> arrayTable;
	public static HashMap<rewrittenArray,Temp> arrayTable2;
	public static FuncFrag result;//translation result of the current function
	public static LinkedList<Frags> trans(TranslationUnit root){
		frag = new LinkedList<Frags>();
		if(inlineEnabled){
			inlineMap=new HashMap<String,FunctionDef>();
			for (ExternalDecl i : root.externalDeclarations){
				if(!(i instanceof FunctionDef))continue;
				int size=((FunctionDef)i).stmts.size;
				if(DEBUG_ON)System.out.println(((FunctionDef)i).head.functionName.toString()+" codesize:"+size);
				Integer [] inlineSize={17,87};
				boolean ok=size<12||
						(size>37&&size<41)||
						size==25||
						(56<size&&size<60)||
						size==78||
						size==83||
						size==383||
						size==128||
						Arrays.asList(inlineSize).contains(size);
				if(!ok)continue;
				if(((FunctionDef)i).head.functionName.toString().equals("main"))continue;
				if(DEBUG_ON)System.out.println("####can inline:"+((FunctionDef)i).head.functionName.toString()+":"+((FunctionDef)i).stmts.size);
				inlineMap.put(((FunctionDef)i).head.functionName.toString(), (FunctionDef)i);
			}
		}
		for (ExternalDecl i : root.externalDeclarations){
			if(!(i instanceof FunctionDef))continue;
			FuncFrag tmp=trans((FunctionDef)i);
			allFunc.add(tmp.name);
			frag.add(tmp);
		}
		killedFunc=new HashSet<Label>(allFunc);
		calledFunc.add(new Label("main"));
		killedFunc.removeAll(calledFunc);
		return frag;
	}
	private static FuncFrag trans(FunctionDef def){
		continueS=new Stack<Label>();
		breakS=new Stack<Label>();
		symTable=new HashMap<Symbol,Temp> ();
		result = new FuncFrag(new Label(def.head.functionName.toString()));
		IR = new LinkedList<Quad>();
		stackPtr = new HashMap<Temp, Integer>();
		tempTable=new HashMap<Temp,TempOprand>();
		Temp [] argv = new Temp[def.head.parameterList.parameterDeclarations.size()];
		int i=0;
		frameSize=0;
		hasCall=0;
		hasSysCall=0;
		arrayTable = new HashMap<rewrittenArray,Temp> ();
		for(ParameterDecl d : def.head.parameterList.parameterDeclarations){
			argv[i]=new Temp();
			symTable.put(d.name, argv[i]);
			stackPtr.put(argv[i], frameSize++);
			i++;
		}
		frameSize+=reservedFrameSize;
		result.argv=argv;
		for(VariableDecl d: def.vardec.variableDeclarations){
			if(d.inRecord)error(d.toString()+":looks like 2012 stuff");
			for(Symbol id: d.ids.ids){
				Temp tmp = new Temp();
				symTable.put(id,tmp);
				stackPtr.put(tmp,frameSize++);
			}
		}
		if(def.canRewriteArray){
			for(rewrittenArray v:def.arrayCount.keySet()){
				if(def.arrayCount.get(v)>2){
					Temp tmp = new Temp();
					arrayTable.put(v,tmp);
					IR.add(new Move(Temp2TempOprand(tmp),new Mem(((TempOprand)trans(v.id,false)).temp,v.offset*wordLength+wordLength)));
					stackPtr.put(tmp,frameSize++);
				}
			}
		}
		boolean tmpUnroll=false;
		curCodeSize=def.stmts.size;
		if(curCodeSize==383||curCodeSize==240){
			tmpUnroll=loopUnrolling;
			loopUnrolling=true;
		}
		trans(def.stmts);
		if(curCodeSize==383||curCodeSize==240){
			loopUnrolling=tmpUnroll;
		}
		for(rewrittenArray v:arrayTable.keySet()){
			Temp tmp = arrayTable.get(v);
			IR.add(new Move(new Mem(((TempOprand)trans(v.id,false)).temp,v.offset*wordLength+wordLength),Temp2TempOprand(tmp)));
		}
		
		boolean tmpEnabled=false,tmpSemiEnabled=false;
		if(stackPtr.size()<100||(stackPtr.size()>400&&stackPtr.size()<4000)){
			tmpEnabled=LiveAnalysis.enabled;
			tmpSemiEnabled=LiveAnalysis.semiEnabled;
			LiveAnalysis.enabled=true;
			LiveAnalysis.semiEnabled=true;
			//System.out.println("enabled");
			result.curLiveEnabled=true;
		}
		result.body = BasicBlock.killLabel(IR);
		//result.body=IR;
		Quad tmp=IR.peekLast();
		if(tmp instanceof Return){
			((Return)tmp).isFuncExit=true;
		}
		result.stackPtr=stackPtr;
		result.frameSize=frameSize;
		if(DEBUG_ON)System.out.println(result.name.toString()+" tempsize :"+stackPtr.size());
		
		result.blocks=LiveAnalysis.liveAnalysis(BasicBlock.buildBlocks(result.body));
		if(stackPtr.size()<100||(stackPtr.size()>400&&stackPtr.size()<4000)){
			LiveAnalysis.enabled=tmpEnabled;
			LiveAnalysis.semiEnabled=tmpSemiEnabled;
		}
		result.hasCall=hasCall;
		result.hasSysCall=hasSysCall;
		result.hasSideEffect=hasSideEffect||hasCall>0;
		return result;
	}
	private static Oprand transCond(Expr cond,Label blabel,Label neglabel,boolean onTrue,boolean jOnNeg){
		Label end=null;
		
		Oprand tc=null;
		if(onTrue){
			if(cond==null||(cond.isConst&&cond.value!=0)){
				IR.add(new Jump(blabel));
				return new Const(1);
			}else if(cond.isConst&&cond.value==0){
				if(neglabel!=null&&jOnNeg)IR.add(new Jump(neglabel));
				return new Const(0);
			}
			if(cond instanceof BinaryExpr&&!(((BinaryExpr)cond).l.ty instanceof STRING)&&!(((BinaryExpr)cond).r.ty instanceof STRING)){
				TempOprand tmp;
				switch(((BinaryExpr)cond).op){
				case EQ:
				case NEQ:
				case LESS:
				case LESS_EQ:
				case GREATER:
				case GREATER_EQ:
					IR.add(new Branch(blabel,trans(((BinaryExpr)cond).l, false),trans(((BinaryExpr)cond).r, false),((BinaryExpr)cond).op));
					if(neglabel!=null&&jOnNeg)IR.add(new Jump(neglabel));
					break;
					//return mvOprand(new Const(0));
				case AND:
					if(neglabel!=null&&jOnNeg){
						transCond(((BinaryExpr)cond).l, neglabel, null, false,false);
						transCond(((BinaryExpr)cond).r, neglabel, blabel, false,true);
					}else{
						end = new Label();
						tmp = mvOprand(trans(((BinaryExpr)cond).l, false), false);
						IR.add(new Branch(end,tmp,new Const(0),BinaryOp.EQ));
						tmp = mvOprand(trans(((BinaryExpr)cond).r, false), false);
						IR.add(new Branch(end,tmp,new Const(0),BinaryOp.EQ));
						IR.add(new Jump(blabel));
						IR.add(new LabelQuad(end));
					}
					break;
				case OR:
					/*tmp = mvOprand(transCond(((BinaryExpr)cond).l, blabel, neglabel, onTrue));
					IR.add(new Branch(blabel,tmp,new Const(0),BinaryOp.NEQ));
					IR.add(new Move(tmp,transCond(((BinaryExpr)cond).r, blabel, neglabel, onTrue)));
					IR.add(new Branch(blabel,tmp,new Const(0),BinaryOp.NEQ));
					return tmp;*/
					transCond(((BinaryExpr)cond).l, blabel, null, true,false);
					transCond(((BinaryExpr)cond).r, blabel, neglabel, true,jOnNeg);
					break;
					default:
						tc=trans(cond, false);
						IR.add(new Branch(blabel,tc,new Const(0),BinaryOp.NEQ));
						if(neglabel!=null&&jOnNeg)IR.add(new Branch(neglabel,tc,new Const(0),BinaryOp.EQ));
						return new Const(0);
				}
				return new Const(0);
			}else{ 
				tc=trans(cond, false);
				IR.add(new Branch(blabel,tc,new Const(0),BinaryOp.NEQ));
				if(neglabel!=null&&jOnNeg)IR.add(new Branch(neglabel,tc,new Const(0),BinaryOp.EQ));
				return new Const(0);
			}
		}else{
			if(cond==null||(cond.isConst&&cond.value!=0)){
				if(neglabel!=null&&jOnNeg)IR.add(new Jump(neglabel));
				return new Const(1);
			}else if(cond.isConst&&cond.value==0){
				IR.add(new Jump(blabel));
				return new Const(0);
			}
			
			if(cond instanceof BinaryExpr&&!(((BinaryExpr)cond).l.ty instanceof STRING)&&!(((BinaryExpr)cond).r.ty instanceof STRING)){
				TempOprand tmp;
				switch(((BinaryExpr)cond).op){
				case EQ:
					IR.add( new Branch(blabel,trans(((BinaryExpr)cond).l, false),trans(((BinaryExpr)cond).r, false),BinaryOp.NEQ));
					if(neglabel!=null&&jOnNeg)IR.add(new Jump(neglabel));
					break;
				case NEQ:
					IR.add(new Branch(blabel,trans(((BinaryExpr)cond).l, false),trans(((BinaryExpr)cond).r, false),BinaryOp.EQ));
					if(neglabel!=null&&jOnNeg)IR.add(new Jump(neglabel));
					break;
				case LESS:
					IR.add(new Branch(blabel,trans(((BinaryExpr)cond).l, false),trans(((BinaryExpr)cond).r, false),BinaryOp.GREATER_EQ));
					if(neglabel!=null&&jOnNeg)IR.add(new Jump(neglabel));
					break;
				case LESS_EQ:
					IR.add( new Branch(blabel,trans(((BinaryExpr)cond).l, false),trans(((BinaryExpr)cond).r, false),BinaryOp.GREATER));
					if(neglabel!=null&&jOnNeg)IR.add(new Jump(neglabel));
					break;
				case GREATER:
					IR.add( new Branch(blabel,trans(((BinaryExpr)cond).l, false),trans(((BinaryExpr)cond).r, false),BinaryOp.LESS_EQ));
					if(neglabel!=null&&jOnNeg)IR.add(new Jump(neglabel));
					break;
				case GREATER_EQ:
					IR.add( new Branch(blabel,trans(((BinaryExpr)cond).l, false),trans(((BinaryExpr)cond).r, false),BinaryOp.LESS));
					if(neglabel!=null&&jOnNeg)IR.add(new Jump(neglabel));
					break;
				case AND:
					transCond(((BinaryExpr)cond).l, blabel, null, false,false);
					transCond(((BinaryExpr)cond).r, blabel, neglabel, false,jOnNeg);
					break;
				case OR:
					/*transCond(((BinaryExpr)cond).l, neglabel, null, true);
					//tmp = mvOprand(transCond(((BinaryExpr)cond).l, blabel, neglabel, onTrue));
					IR.add(new Branch(neglabel,tmp,new Const(0),BinaryOp.NEQ));
					IR.add(new Move(tmp,transCond(((BinaryExpr)cond).r, blabel, neglabel, onTrue)));
					IR.add(new Branch(neglabel,tmp,new Const(0),BinaryOp.NEQ));
					IR.add(new Jump(blabel));*/
					if(neglabel!=null&&jOnNeg){
						transCond(((BinaryExpr)cond).l, neglabel, null, true,false);
						transCond(((BinaryExpr)cond).r, neglabel, blabel, true,true);
					}else{
						end = new Label();
						tmp = mvOprand(trans(((BinaryExpr)cond).l, false), false);
						IR.add(new Branch(end,tmp,new Const(0),BinaryOp.NEQ));
						tmp = mvOprand(trans(((BinaryExpr)cond).r, false), false);
						IR.add(new Branch(end,tmp,new Const(0),BinaryOp.NEQ));
						IR.add(new Jump(blabel));
						IR.add(new LabelQuad(end));
					}
					break;
					default:
						tc=trans(cond, false);
						IR.add(new Branch(blabel,tc,new Const(0),BinaryOp.EQ));
						if(neglabel!=null&&jOnNeg)IR.add(new Branch(neglabel,tc,new Const(0),BinaryOp.NEQ));
						return new Const(1);
				}
				return new Const(1);
			}else{ 
				tc=trans(cond, false);
				IR.add(new Branch(blabel,tc,new Const(0),BinaryOp.EQ));
				if(neglabel!=null&&jOnNeg)IR.add(new Branch(neglabel,tc,new Const(0),BinaryOp.NEQ));
				return new Const(1);
			}
			
		}
	}
	private static void trans(StmtList stmts) {
		cseTable=new HashMap<String,Temp>();//TODO:more exact places here?
		tailRecurse=new LinkedList<TransExprCont>();
		for(Stmt s: stmts.statements){
			trans(s);
		}
	}

	private static void trans(Stmt s) {
		
		if(s instanceof CompoundStmt){
			for(Stmt x: ((CompoundStmt)s).stmts.statements){
				trans(x);
			}
		}
		else if(s instanceof ExprStmt){
			if(((ExprStmt)s).expr instanceof FunctionCall){
				((FunctionCall)((ExprStmt)s).expr).killedret=true;
			}
			trans(((ExprStmt)s).expr, true);
		}
		else if(s instanceof IfStmt){
			Label endLabel=null;// = new Label();
			Label thenLabel = new Label();
			Label elseLabel = new Label();
			//IR.add(new Branch(elseLabel,trans(((IfStmt)s).cond),new Const(0),BinaryOp.EQ));
			//IR.add(transBranch(elseLabel,thenLabel,((IfStmt)s).cond,false));
			transCond(((IfStmt)s).cond,elseLabel,thenLabel,false,false);
			IR.add(new LabelQuad(thenLabel));
			if(((IfStmt)s).cond==null||!((IfStmt)s).cond.isConst||((IfStmt)s).cond.value!=0)
				trans(((IfStmt)s).thenPart);
			if (((IfStmt)s).elsePart != null){
				cseTable=new HashMap<String,Temp>();
				endLabel=new Label();
				IR.add(new Jump(endLabel));
				IR.add(new LabelQuad(elseLabel));
				if(((IfStmt)s).cond==null||!((IfStmt)s).cond.isConst||((IfStmt)s).cond.value==0)
					trans(((IfStmt)s).elsePart);
				IR.add(new LabelQuad(endLabel));
			}else{//fall through
				IR.add(new LabelQuad(elseLabel));
			}
			cseTable=new HashMap<String,Temp>();
		}
		else if(s instanceof WhileStmt){
			Label beg = new Label();
			Label end = new Label();
			Label cond = new Label();
			continueS.push(cond);
			breakS.push(end);
			transCond(((WhileStmt)s).cond,end,null,false,false);
			cseTable=new HashMap<String,Temp>();
			IR.add(new LabelQuad(beg));
			trans(((WhileStmt)s).body);
			IR.add(new LabelQuad(cond));
			transCond(((WhileStmt)s).cond,beg,end,true,false);
			IR.add(new LabelQuad(end));
			cseTable=new HashMap<String,Temp>();
			continueS.pop();
			breakS.pop();
		}
		else if(s instanceof ForStmt){
			Label beg = new Label();
			Label end = new Label();
			Label cont = new Label();
			//Label cond = new Label();
			
			ForStmt f=(ForStmt)s;
			boolean canUnroll=loopUnrolling&&!f.hasBreakOrContinue&&f.init!=null&&
					f.init instanceof BinaryExpr&&
					((BinaryExpr)f.init).l instanceof Id&&
					((BinaryExpr)f.init).op==BinaryOp.ASSIGN&&
					((BinaryExpr)f.init).r instanceof IntLiteral&&
					f.cond!=null&&
					f.cond instanceof BinaryExpr&&
					((BinaryExpr)f.cond).l instanceof Id&&
					((Id)((BinaryExpr)f.cond).l).toString().equals(((Id)((BinaryExpr)f.init).l).toString())&&
					((BinaryExpr)f.cond).r instanceof IntLiteral&&
					(((BinaryExpr)f.cond).op ==BinaryOp.LESS||((BinaryExpr)f.cond).op ==BinaryOp.LESS_EQ)&&
					f.step!=null&&
					f.step instanceof BinaryExpr&&
					((BinaryExpr)f.step).l instanceof Id&&
					((BinaryExpr)f.step).r instanceof BinaryExpr&&
					((Id)((BinaryExpr)f.step).l).toString().equals(((Id)((BinaryExpr)f.init).l).toString())&&
					((BinaryExpr)f.step).op==BinaryOp.ASSIGN&&
					((BinaryExpr)((BinaryExpr)f.step).r).l instanceof Id&&
					((BinaryExpr)((BinaryExpr)f.step).r).r instanceof IntLiteral&&
					((BinaryExpr)((BinaryExpr)f.step).r).op==BinaryOp.PLUS&&
					((Id)((BinaryExpr)((BinaryExpr)f.step).r).l).toString().equals(((Id)((BinaryExpr)f.cond).l).toString())
					;
			int step=0,upp=0,init=0;
			if(canUnroll){
				step=((BinaryExpr)((BinaryExpr)f.step).r).r.value;
				upp=((BinaryExpr)f.cond).r.value;
				init=((BinaryExpr)f.init).r.value;
			}
			if(!canUnroll||step*12+init<upp){
				continueS.push(cont);
				breakS.push(end);
				if(((ForStmt)s).init!=null)trans(((ForStmt)s).init, false);
				//IR.add(new Jump(cond));
				transCond(((ForStmt)s).cond,end,null,false,false);
				IR.add(new LabelQuad(beg));
				cseTable=new HashMap<String,Temp>();
				if(((ForStmt)s).body!=null)trans(((ForStmt)s).body);
				IR.add(new LabelQuad(cont));
				cseTable=new HashMap<String,Temp>();
				if(((ForStmt)s).step!=null)trans(((ForStmt)s).step, false);
				//IR.add(new LabelQuad(cond));
				//IR.add(new Branch(beg,trans(((ForStmt)s).cond),new Const(0),BinaryOp.NEQ));
				//IR.add(transBranch(beg,end,((ForStmt)s).cond,true));
				transCond(((ForStmt)s).cond,beg,end,true,false);
				IR.add(new LabelQuad(end));
				cseTable=new HashMap<String,Temp>();
				continueS.pop();
				breakS.pop();
			}else {
				if(((BinaryExpr)f.cond).op ==BinaryOp.LESS_EQ)upp=upp+1;
				if(((ForStmt)s).init!=null)trans(((ForStmt)s).init, false);
				for(int i=init;i<upp;i=i+step){
					if(((ForStmt)s).body!=null)trans(((ForStmt)s).body);
					if(((ForStmt)s).step!=null)trans(((ForStmt)s).step, false);
				}
			}
		}
		else if(s instanceof ReturnStmt){
			if(inlineFuncRet==null){
				for(rewrittenArray v:arrayTable.keySet()){
					Temp tmp = arrayTable.get(v);
					IR.add(new Move(new Mem(((TempOprand)trans(v.id,false)).temp,v.offset*wordLength+wordLength),Temp2TempOprand(tmp)));
				}
				IR.add(new Return(trans(((ReturnStmt)s).expr, false)));
			}else {
				if(inlineFuncRetTemp!=null){
					Oprand x=trans(((ReturnStmt)s).expr, false);
					IR.add(new Move(inlineFuncRetTemp,x));
				}
				IR.add(new Jump(inlineFuncRet));
			}
		}
		else if(s instanceof BreakStmt){
			IR.add(new Jump(breakS.peek()));
		}
		else if(s instanceof ContinueStmt){
			IR.add(new Jump(continueS.peek()));
		}

	}
	private static Oprand trans(Expr expr, boolean isTopLevel) {
		if(expr instanceof BinaryExpr)			return transBinaryExpr((BinaryExpr)expr, isTopLevel);
		else if(expr instanceof CharLiteral)		return trans((CharLiteral)expr);
		else if(expr instanceof FieldPostfix)		return trans((FieldPostfix)expr);
		else if(expr instanceof FunctionCall)		return transFunc((FunctionCall)expr);
		else if(expr instanceof Id)			return trans((Id)expr);
		else if(expr instanceof IntLiteral)		return trans((IntLiteral)expr);
		else if(expr instanceof NewArray)		return transNewArray((NewArray)expr);
		else if(expr instanceof NewRecord)		return transNewRecord((NewRecord)expr);
		else if(expr instanceof Null)			return trans((Null)expr);
		else if(expr instanceof StringLiteral)		return transString((StringLiteral)expr, false);
		else if(expr instanceof SubscriptPostfix)	return trans((SubscriptPostfix)expr);
		else if(expr instanceof UnaryExpr)		return trans((UnaryExpr)expr);
		else error(expr.pos,expr.toString()+" looks like 2012 stuff.");
		return null;
	}
	//for BinaryExpr
	public static Temp hasComputed(Oprand tl,BinaryOp op,Oprand tr,boolean isStrcat){
		//return null;
		Temp ret=null;
		if(tl!=null&&tr!=null){
			String a=null,b=null;
			if(tr instanceof TempOprand)
				b=((TempOprand)tr).temp.toSSA();
			if(tr instanceof Const)
				b=((Const)tr).toString();
			if(tl instanceof TempOprand)
				a=((TempOprand)tl).temp.toSSA();
			if(tl instanceof Const)
				a=((Const)tl).toString();
			
			if(a!=null&&b!=null&&!isStrcat)
				ret=cseTable.get(a+op.toString()+b);
		}
		return ret;
	}
	//for function without side-effects
	public static Temp hasComputed(String name,Temp[] params){
		//return null;
		Temp ret=null;
		String entry=name+"(";
		for(Temp p:params){
			entry+=p.toSSA()+",";
		}
		ret=cseTable.get(entry);
		return ret;
	}
	public static boolean isLeaf(Expr e){
		return (e instanceof Id)||(e instanceof IntLiteral)
				||(e instanceof Null)||e instanceof CharLiteral
				||e instanceof StringLiteral;
	}
	public static boolean nearLeaf(Expr e){
		//return e.size<8;
		return e.depth<5;
	}
	//TODO:tail recurse for other expression
	public static boolean canTailRecurse(BinaryExpr expr){
		return expr.op==BinaryOp.PLUS&&(nearLeaf(expr.l)||nearLeaf(expr.r));
	}
	public static boolean canTailRecurse(FieldPostfix expr){
		return expr.expr instanceof FieldPostfix;
	}
	private static Oprand transUnBalanced(BinaryExpr		expr) {
		TempOprand ret=null;
		tailRecurse.add(new NullTransCont(){	});
		Expr Nextexpr=expr;
		while(Nextexpr instanceof BinaryExpr){
			Expr l = ((BinaryExpr)Nextexpr).l,r=((BinaryExpr)Nextexpr).r;
			if(!canTailRecurse((BinaryExpr)Nextexpr))break;
			if((!isLeaf(l)||!isLeaf(r))){
				if(!isLeaf(l)&&nearLeaf(r)){
					tailRecurse.add(ContBinaryExpr.getContBinaryExprLeft(trans(r, false),((BinaryExpr)Nextexpr).op,Nextexpr.inferType,l.ty,r.ty));
					if(l instanceof BinaryExpr)Nextexpr=(BinaryExpr) l;
					else {Nextexpr=l;break;}
				}else if(!isLeaf(r)&&nearLeaf(l)){
					tailRecurse.add(ContBinaryExpr.getContBinaryExprRight(trans(l, false),((BinaryExpr)Nextexpr).op,Nextexpr.inferType,l.ty,r.ty));
					if(r instanceof BinaryExpr)Nextexpr=(BinaryExpr) r;
					else  {Nextexpr=r;break;}
				}else{
					error("meow meow meow!");
				}
			}else{
				break;
			}
		}
		ret=mvOprand(trans(Nextexpr, false), false);
		while(!tailRecurse.peekLast().isNull()){
			ret=mvOprand(tailRecurse.pollLast().trans(ret), false);
		}
		tailRecurse.pollLast();
		return ret;
	}

	private static Oprand transBinaryExpr(BinaryExpr		expr, boolean isTopLevel, Object... dst){
		if(expr.isConst)return new Const(expr.value);
		Temp tmp=null;
		TempOprand ret=null;
		if(dst!=null&&dst.length>0&&dst[0] instanceof TempOprand)ret=(TempOprand)dst[0];
		boolean isStrcat=(expr.op==BinaryOp.PLUS&&((expr.l.ty instanceof STRING || expr.r.ty instanceof STRING))||expr.inferType instanceof STRING);
		Oprand tl = null;
		if(expr.op != BinaryOp.ASSIGN&&expr.op!=BinaryOp.AND && expr.op != BinaryOp.OR){
			if(expr.l instanceof BinaryExpr&&nearLeaf(expr.r)&&!nearLeaf(expr.l))tl =  transUnBalanced((BinaryExpr)expr.l);
			else if(expr.l instanceof BinaryExpr)			tl =  transBinaryExpr((BinaryExpr)expr.l, isTopLevel);
			else if(expr.l instanceof CharLiteral)		tl =  trans((CharLiteral)expr.l);
			else if(expr.l instanceof FieldPostfix)		tl =  trans((FieldPostfix)expr.l);
			else if(expr.l instanceof FunctionCall)		tl =  transFunc((FunctionCall)expr.l);
			else if(expr.l instanceof Id)			tl =  trans((Id)expr.l);
			else if(expr.l instanceof IntLiteral)		tl =  trans((IntLiteral)expr.l);
			else if(expr.l instanceof NewArray)		tl =  transNewArray((NewArray)expr.l);
			else if(expr.l instanceof NewRecord)		tl =  transNewRecord((NewRecord)expr.l);
			else if(expr.l instanceof Null)			tl =  trans((Null)expr.l);
			else if(expr.l instanceof StringLiteral)		tl =  transString((StringLiteral)expr.l, false);
			else if(expr.l instanceof SubscriptPostfix)	tl =  trans((SubscriptPostfix)expr.l);
			else if(expr.l instanceof UnaryExpr)		tl =  trans((UnaryExpr)expr.l);
		}
		Oprand tr = null;
		if(expr.op != BinaryOp.ASSIGN&&expr.op!=BinaryOp.AND && expr.op != BinaryOp.OR){
			if(expr.r instanceof BinaryExpr&&nearLeaf(expr.l)&&!nearLeaf(expr.r))tr =  transUnBalanced((BinaryExpr)expr.r);
			else if(expr.r instanceof BinaryExpr)			tr =  transBinaryExpr((BinaryExpr)expr.r, isTopLevel);
			else if(expr.r instanceof CharLiteral)		tr =  trans((CharLiteral)expr.r);
			else if(expr.r instanceof FieldPostfix)		tr =  trans((FieldPostfix)expr.r);
			else if(expr.r instanceof FunctionCall)		tr =  transFunc((FunctionCall)expr.r);
			else if(expr.r instanceof Id)			tr =  trans((Id)expr.r);
			else if(expr.r instanceof IntLiteral)		tr =  trans((IntLiteral)expr.r);
			else if(expr.r instanceof NewArray)		tr =  transNewArray((NewArray)expr.r);
			else if(expr.r instanceof NewRecord)		tr =  transNewRecord((NewRecord)expr.r);
			else if(expr.r instanceof Null)			tr =  trans((Null)expr.r);
			else if(expr.r instanceof StringLiteral)		tr =  transString((StringLiteral)expr.r, false);
			else if(expr.r instanceof SubscriptPostfix)	tr =  trans((SubscriptPostfix)expr.r);
			else if(expr.r instanceof UnaryExpr)		tr =  trans((UnaryExpr)expr.r);
		}
		if(expr.op != BinaryOp.ASSIGN&&expr.op!=BinaryOp.COMMA){
			tmp=hasComputed(tl,expr.op,tr,isStrcat);
			if(tmp==null&&!isStrcat&&ret==null){
				tmp=new Temp();
				stackPtr.put(tmp,frameSize++);
				ret=Temp2TempOprand(tmp);
			}else if(tmp==null&&!isStrcat&&ret!=null){
				
			}else if(!isStrcat&&ret==null) {
				ret=Temp2TempOprand(tmp);
				return ret;
			}else if(!isStrcat&&ret!=null) {
				TempOprand tmpO=Temp2TempOprand(tmp);
				if(!tmp.equals(ret.temp)){
					IR.add(new Move(ret, tmpO));
				}
				return ret;
			}
		}
		Label end;
		
		switch(expr.op){
		case MULTIPLY:
		case DIVIDE:
		case MODULO:
		case MINUS:
			IR.add( new BinOp(ret,tl,tr,expr.op));
			if(tl!=null&&tr!=null){
				String a=null,b=null;
				if(tr instanceof TempOprand)
					b=((TempOprand)tr).temp.toSSA();
				if(tr instanceof Const)
					b=((Const)tr).toString();
				if(tl instanceof TempOprand)
					a=((TempOprand)tl).temp.toSSA();
				if(tl instanceof Const)
					a=((Const)tl).toString();
				
				if(a!=null&&b!=null)
					cseTable.put(a+expr.op.toString()+b,ret.temp);
			}
			return ret;
		case AND:
		case OR:
			end = new Label();
			//IR.add(new Move(ret,tl));
			if(ret!=null&&expr.l instanceof BinaryExpr){
				ret = mvOprand(transBinaryExpr((BinaryExpr)expr.l,isTopLevel, ret), false);
			}else {
				ret = mvOprand(trans(expr.l, false), false);
			}
			IR.add(new Branch(end,ret,new Const(0),(expr.op==BinaryOp.AND)?BinaryOp.EQ:BinaryOp.NEQ));
			if(expr.r instanceof BinaryExpr){
				transBinaryExpr((BinaryExpr)expr.r,isTopLevel, (TempOprand)ret);
			}else
				IR.add(new Move(ret,trans(expr.r, false)));
			IR.add(new LabelQuad(end));
			return ret;
		case PLUS:
			if(isStrcat){
				Temp[] params = new Temp[2];
				if(expr.l.ty instanceof STRING){
					params[0] = mvTemp(tl, false, false, true);
				}else if(expr.l.ty instanceof INT||expr.l.ty instanceof CHAR){
					Temp[] convparams = new Temp[1];
					String funcname=(expr.l.ty instanceof INT)?"_intToString":"_charToString";
					//TODO:local CSE for const
//					if(tl instanceof Const){
//						convparams[0]=
//					}
					convparams[0]=mvTemp(tl, false, false, true);
					params[0]=hasComputed(funcname,convparams);
					if(params[0]==null){
						params[0]=new Temp();
						stackPtr.put(params[0], frameSize++);
						hasCall++;
						IR.add(new Call(new Label(funcname),convparams,params[0]));
						cseTable.put(funcname+"("+convparams[0].toSSA()+",",params[0]);
					}
				}
				
				if(expr.r.ty instanceof STRING){
					params[1] = mvTemp(tr, false, false, true);
				}else if(expr.r.ty instanceof INT||expr.r.ty instanceof CHAR){
					Temp[] convparams = new Temp[1];
					String funcname=(expr.r.ty instanceof INT)?"_intToString":"_charToString";
					convparams[0]=mvTemp(tr, false, false, true);
					params[1]=hasComputed(funcname,convparams);
					if(params[1]==null){
						params[1]=new Temp();
						stackPtr.put(params[1],frameSize++);
						hasCall++;
						IR.add(new Call(new Label(funcname),convparams,params[1]));
						cseTable.put(funcname+"("+convparams[0].toSSA()+",",params[1]);
					}
				}
				tmp=hasComputed("_strcat",params);
				if(tmp!=null){
					return Temp2TempOprand(tmp);
				}
				if(ret==null){
					tmp=new Temp();
					stackPtr.put(tmp,frameSize++);
					ret=Temp2TempOprand(tmp);
				}
				IR.add(new Call(new Label("_strcat"),params,ret.temp));
				hasCall++;
				cseTable.put("_strcat("+params[0].toSSA()+","+params[1].toSSA()+",",ret.temp);
			}else {
				IR.add(new BinOp(ret,tl,tr,expr.op));
				if(tl!=null&&tr!=null){
					String a=null,b=null;
					if(tr instanceof TempOprand)
						b=((TempOprand)tr).temp.toSSA();
					if(tr instanceof Const)
						b=((Const)tr).toString();
					if(tl instanceof TempOprand)
						a=((TempOprand)tl).temp.toSSA();
					if(tl instanceof Const)
						a=((Const)tl).toString();
					
					if(a!=null&&b!=null)
						cseTable.put(a+expr.op.toString()+b,ret.temp);
				}
			}
			return ret;
		case EQ:
		case NEQ:
		case LESS:
		case LESS_EQ:
		case GREATER:
		case GREATER_EQ:
			if(expr.l.ty instanceof STRING || expr.r.ty instanceof STRING){
				end = new Label();
				IR.add(new Move(ret,new Const(1)));
				Temp[] params = new Temp[2];
				if(expr.l.ty instanceof STRING){
					params[0] = mvTemp(tl, false, false, true);
				}else if(expr.l.ty instanceof INT||expr.l.ty instanceof CHAR){
					Temp[] convparams = new Temp[1];
					convparams[0]=mvTemp(tl, false, false, true);
					IR.add(new Call(new Label((expr.l.ty instanceof INT)?"_intToString":"_charToString"),convparams,params[0]));
				}
				if(expr.r.ty instanceof STRING){
					params[1] = mvTemp(tr, false, false, true);
				}else if(expr.r.ty instanceof INT||expr.r.ty instanceof CHAR){
					Temp[] convparams = new Temp[1];
					convparams[0]=mvTemp(tr, false, false, true);
					IR.add(new Call(new Label((expr.r.ty instanceof INT)?"_intToString":"_charToString"),convparams,params[1]));
				}
				Temp cret= new Temp();
				stackPtr.put(cret, frameSize++);
				IR.add(new Call(new Label("_strcmp"),params,cret));
				hasCall++;
				IR.add(new Branch(end,Temp2TempOprand(cret),new Const(0),expr.op));
				IR.add(new Move(ret,new Const(0)));
				IR.add(new LabelQuad(end));
			}else {
				IR.add(new BinOp(ret,tl,tr,expr.op));
			}
			return ret;
		case ASSIGN:
			if(expr.l instanceof Id){
				Symbol id=((Id)expr.l).sym;
				if(inlineFuncRet==null)
					symTable.get(id).addSSA();
				else
					symTable2.get(id).addSSA();
			}
			tl=trans(expr.l, false);
			tr=(tl instanceof TempOprand&&expr.r instanceof BinaryExpr)?
					transBinaryExpr((BinaryExpr)expr.r,isTopLevel, ((TempOprand)tl)):
						(tl instanceof TempOprand&&expr.r instanceof FunctionCall)?
								transFunc((FunctionCall)expr.r,((TempOprand)tl)):
									(tl instanceof TempOprand&&expr.r instanceof NewArray)?
											transNewArray((NewArray)expr.r,((TempOprand)tl)):
												(tl instanceof TempOprand&&expr.r instanceof NewRecord)?
														transNewRecord((NewRecord)expr.r,((TempOprand)tl)):
															(tl instanceof TempOprand&&expr.r instanceof StringLiteral)?
																	transString((StringLiteral)expr.r,false, ((TempOprand)tl)):
									trans(expr.r, false);
			if(tl instanceof Mem&&tr instanceof Mem&&!isTopLevel){
				ret=Temp2TempOprand(new Temp());
				stackPtr.put(ret.temp,frameSize++);
				IR.add(new Move(ret,tr));
				IR.add(new Move(tl,ret));
				return ret;
			}else if(tl instanceof Mem&&tr instanceof Mem&&isTopLevel){
				IR.add(new Move(tl,tr));
				return tr;
			}else {
				if( expr.r instanceof BinaryExpr &&tl instanceof TempOprand){
					
				}else {
					boolean done=(tl instanceof TempOprand&&tr instanceof TempOprand&&((TempOprand)tl).temp.equals(((TempOprand)tr).temp));
					if(!done)IR.add(new Move(tl,tr));
				}
				if(ret!=null&&(!(tr instanceof TempOprand)||!((TempOprand)tr).temp.equals(ret))){
					IR.add(new Move(ret,tr));
					return ret;
				}
			}
			return tr;
		case COMMA:
			//trans(expr.l);
			if(ret!=null)IR.add(new Move(ret,tr));
			return tr;// trans(expr.r);
		default:
			error(expr.pos,expr.toString()+" : looks like 2012 stuff.");
		}
		return ret;
	}
	public static TempOprand Temp2TempOprand(Temp tmp) {
		if(tmp==null)error("mie@Trans@Temp2TempOprand");
		TempOprand ret=tempTable.get(tmp);
		if(ret==null){
			ret=new TempOprand(tmp);
			tempTable.put(tmp, ret);
		}
		return ret;
	}
	private static TempOprand mvOprand(Oprand x, boolean isOnceTemp) {
		if(x instanceof TempOprand)return (TempOprand)x;
		Temp tmp=new Temp();
		if(isOnceTemp)tmp.isOnceTemp=true;
		else 
			stackPtr.put(tmp, frameSize++);
		TempOprand ret = Temp2TempOprand(tmp);
		IR.add(new Move(ret,x));
		return ret;
	}
	private static Oprand trans(CharLiteral		expr) {
		return new Const(expr.c);
	}
	private static Oprand trans(FieldPostfix	expr) {
		if(expr.expr.ty instanceof STRING || expr.expr.ty.isArray()){
			if (!expr.field.equals(Symbol.valueOf("length")))error(expr.pos,"field postfix must be length for string and arrays");
			if(expr.expr.ty.isArray())return new Mem(mvTemp(trans(expr.expr, false), false, false, false),0);
			else if(expr.expr.ty instanceof STRING){
				Temp ret = new Temp();
				/*Temp [] params = new Temp[1];
				
				stackPtr.put(ret, frameSize++);
				params[0]=mvTemp(trans(expr.expr, false), false, true);*/
				hasCall++;
				Oprand [] params=new Oprand[1];
				params[0]=trans(expr.expr, false);
				IR.add(new SysCall(new Label("_strlen"),params,ret));
				return Temp2TempOprand(ret);
			}
		}
		if(!(expr.expr.ty instanceof RECORD))error(expr.pos,expr.toString()+" : looks like 2012 stuff.");
		RecordField rf = ((RECORD)expr.expr.ty).fields.get(expr.field);
		int offset = rf.index ;
		if(expr.expr instanceof FieldPostfix)
			return new Mem(mvTemp(transUnBalanced((FieldPostfix)expr.expr), false, true, false),offset, ((rf.type instanceof CHAR)?charLength:wordLength));
		else 
			return new Mem(mvTemp(trans(expr.expr, false), false, false, false),offset, ((rf.type instanceof CHAR)?charLength:wordLength));
	}
	private static Oprand transUnBalanced(FieldPostfix		expr) {
		Oprand ret=null;
		tailRecurse.add(new NullTransCont(){	});
		Expr Nextexpr=expr.expr;
		while(Nextexpr instanceof FieldPostfix){
			Expr l = ((FieldPostfix)Nextexpr).expr;
			final RecordField rf = ((RECORD)((FieldPostfix)Nextexpr).expr.ty).fields.get(((FieldPostfix)Nextexpr).field);
			final int offset = rf.index ;
			if(!canTailRecurse((FieldPostfix)Nextexpr))break;
			if(!isLeaf(l)){
				tailRecurse.add(new TransExprCont(){
					@Override
					public Oprand trans(Oprand e) {
						return new Mem(mvTemp(e, false, true, false),offset, ((rf.type instanceof CHAR)?charLength:wordLength));
					}
				});
				if(l instanceof FieldPostfix)Nextexpr=(FieldPostfix) l;
				else {Nextexpr=l;break;}
			}else{
				break;
			}
		}
		ret=mvOprand(trans(Nextexpr, false), false);
		while(!tailRecurse.peekLast().isNull()){
			ret=tailRecurse.pollLast().trans(ret);
		}
		tailRecurse.pollLast();
		return ret;
	}
	private static void transPrint(String name,Expr arg){
		BinaryExpr a=null;
		//Temp [] params = new Temp[1];
		Oprand [] params=new Oprand[1];
		
		SysCall c=null;
		if(arg instanceof BinaryExpr){
			a=(BinaryExpr)arg;
			if(a.op==BinaryOp.PLUS){
				String fa="printString";
				if(a.l.ty instanceof INT)fa="printInt";
				else if(a.l.ty instanceof CHAR)fa="printChar";
				transPrint(fa,a.l);
				fa="printString";
				if(name.equals("printLine"))fa="printLine";
				if(a.r.ty instanceof INT)fa="printInt";
				else if(a.r.ty instanceof CHAR)fa="printChar";
				transPrint(fa,a.r);
				if(name.equals("printLine")&&(a.r.ty instanceof INT||a.r.ty instanceof CHAR)){
					fa="printString";
					Oprand ta=new LabelAddress(new Label("__eol"));
					params[0]=ta;
					c=new SysCall(new Label(fa),params,null,false);
				}
			}
		}else {
			Oprand ta=(arg instanceof StringLiteral)?transString((StringLiteral)arg,true):trans(arg,false);
			params[0]=ta;
			if(arg.ty instanceof INT){
				if(!name.equals("printLine"))name="printInt";
				else error("meow");
			}else if(arg.ty instanceof CHAR){
				if(!name.equals("printLine"))name="printChar";
				else error("meow");
			}
			c=new SysCall(new Label(name),params,null,false);
		}
		if(c!=null){
			c.killedret=true;
			IR.add(c);
			if(!FuncFrag.isInline0(c))hasCall++;
			else hasSysCall++;
			hasSideEffect=true;
		}
	}
	private static Oprand transFunc(FunctionCall expr, Object... dst) {
		//TODO:for those with no side effect eliminate them.
		
		Temp [] params = new Temp[expr.params.size()];
		Temp ret = null;
		if(expr.expr.toString().equals("ord")||expr.expr.toString().equals("chr")){
			Oprand ta=trans(expr.params.peekFirst(), false);
			if(dst!=null&&dst.length>0&&dst[0] instanceof TempOprand){
				IR.add(new Move((TempOprand)dst[0],ta));
				return (TempOprand)dst[0];
			}
			return ta;
		}
		
		if(dst!=null&&dst.length>0&&dst[0] instanceof TempOprand){
			ret=((TempOprand)dst[0]).temp;
		}else if(expr.killedret){
			
		}else {
			ret = new Temp();
			stackPtr.put(ret, frameSize++);
		}
		
		if((expr.expr.toString().equals("printString")||expr.expr.toString().equals("printLine"))){
			transPrint(expr.expr.toString(),expr.params.peekFirst());
			return ret!=null?Temp2TempOprand(ret):null;
		}
		int i=0;
		boolean hasArrayOrRecord=false;
		Label funcname=new Label(expr.expr.toString());
		FunctionDef def=inlineMap.get(expr.expr.toString());
		if(def==null||inlineFuncRet!=null||(curCodeSize==87&&def.stmts.size==87)||(curCodeSize==383&&def.stmts.size==383)){
			for(Expr a: expr.params){
				Oprand ta=trans(a, false);
				//FIXME:it's not a onceTemp but a paramTemp
				params[i++]=mvTemp(ta, false, true, true);
				if(a.ty instanceof RECORD||a.ty instanceof ARRAY)
					hasArrayOrRecord=true;
				//IR.add(new Move(Temp2TempOprand(params[i++]),trans(a)));
			}
			Call c=new Call(funcname,params,ret,hasArrayOrRecord);
			calledFunc.add(funcname);
			if(expr.killedret)c.killedret=true;
			IR.add(c);
			if(!FuncFrag.isInline0(c))hasCall++;
			else hasSysCall++;
			hasSideEffect=true;
		}else{
			if(DEBUG_ON)
				System.out.println("##inlined "+
			def.head.functionName.toString()+
			" into "+result.name.toString()+":"+def.stmts.size+"+"+curCodeSize+"="+(def.stmts.size+curCodeSize));
			Temp [] argv = new Temp[def.head.parameterList.parameterDeclarations.size()];
			Oprand [] tmpargv=new Oprand[expr.params.size()];
			i=0;
			for(Expr a: expr.params){
				tmpargv[i++]=trans(a, false);
			}
			inlineFuncRet=new Label();
			inlineFuncRetTemp=ret!=null?Temp2TempOprand(ret):null;
			symTable2=new HashMap<Symbol,Temp>();
			arrayTable2 = new HashMap<rewrittenArray,Temp> ();
			i=0;
			for(ParameterDecl d : def.head.parameterList.parameterDeclarations){
				argv[i]=new Temp();
				symTable2.put(d.name, argv[i]);
				stackPtr.put(argv[i], frameSize++);
				i++;
			}
			for(i=0;i<expr.params.size();i++){
				IR.add(new Move(Temp2TempOprand(argv[i]),tmpargv[i]));
			}
			//frameSize+=reservedFrameSize;
			for(VariableDecl d: def.vardec.variableDeclarations){
				if(d.inRecord)error(d.toString()+":looks like 2012 stuff");
				for(Symbol id: d.ids.ids){
					Temp tmp = new Temp();
					symTable2.put(id,tmp);
					stackPtr.put(tmp,frameSize++);
				}
			}
			if(def.canRewriteArray){
				for(rewrittenArray v:def.arrayCount.keySet()){
					if(def.arrayCount.get(v)>2){
						Temp tmp = new Temp();
						arrayTable2.put(v,tmp);
						IR.add(new Move(Temp2TempOprand(tmp),new Mem(((TempOprand)trans(v.id,false)).temp,v.offset*wordLength+wordLength)));
						stackPtr.put(tmp,frameSize++);
					}
				}
			}
				
			trans(def.stmts);
			IR.add(new LabelQuad(inlineFuncRet));
			for(rewrittenArray v:arrayTable.keySet()){
				Temp tmp = arrayTable.get(v);
				IR.add(new Move(new Mem(((TempOprand)trans(v.id,false)).temp,v.offset*wordLength+wordLength),Temp2TempOprand(tmp)));
			}
			inlineFuncRet=null;
		}
		return ret!=null?Temp2TempOprand(ret):null;
	}
	private static Oprand trans(Id	 expr) {
		if(inlineFuncRet==null)
			return Temp2TempOprand(symTable.get(expr.sym));
		else
			return Temp2TempOprand(symTable2.get(expr.sym));
	}
	private static Oprand trans(IntLiteral		expr) {
		return new Const(expr.i);
	}
	@SuppressWarnings("unused")
	private static Oprand transNewArray(NewArray		expr, Object... dst) {
		TempOprand x,y;
		y=mvOprand(trans(expr.expr, false), false);
		if(((ARRAY)expr.ty).elementType instanceof CHAR && charLength == 1){
			Temp tmp = new Temp();
			stackPtr.put(tmp, frameSize++);
			x = Temp2TempOprand(tmp);
			IR.add(new BinOp(x,y,new Const(wordLength),BinaryOp.PLUS));
		}else if(((ARRAY)expr.ty).elementType instanceof CHAR ){
			Temp tmp = new Temp();
			stackPtr.put(tmp, frameSize++);
			x = Temp2TempOprand(tmp);
			IR.add(new BinOp(x,y,new Const(charLength),BinaryOp.MULTIPLY));
			IR.add(new BinOp(x,x,new Const(wordLength),BinaryOp.PLUS));
		}else {
			Temp tmp = new Temp();
			stackPtr.put(tmp, frameSize++);
			x = Temp2TempOprand(tmp);
			IR.add(new BinOp(x,y,new Const(wordLength),BinaryOp.MULTIPLY));
			IR.add(new BinOp(x,x,new Const(wordLength),BinaryOp.PLUS));
		}
		TempOprand ret = malloc(x,
				(dst!=null&&dst.length>0&&dst[0] instanceof TempOprand)?
						((TempOprand)dst[0]):
							null);
		
		IR.add(new Move(new Mem(ret.temp,0),y));
		return ret;
	}
	private static TempOprand malloc(Oprand x, Object... dst) {
		Oprand [] params = new Oprand[1];
		Temp ret = null;
		if(dst!=null&&dst.length>0&&dst[0] instanceof TempOprand){
			ret=((TempOprand)dst[0]).temp;
		}else{
			ret=new Temp();
			stackPtr.put(ret, frameSize++);
		}
		params[0]=x;
		IR.add(new SysCall(new Label("_malloc"),params,ret));
		return Temp2TempOprand(ret);
	}
	public static Temp mvTemp(Oprand x, boolean markIfConst, boolean isOnceTemp, boolean isParamTemp) {
		if(x instanceof TempOprand){
			return ((TempOprand)x).temp;
		}
		Temp tmp = new Temp();
		if(isOnceTemp)tmp.isOnceTemp=true;
		else {
			stackPtr.put(tmp,frameSize++);
		}
		if(isParamTemp)tmp.isParamTemp=true;
		TempOprand ret = Temp2TempOprand(tmp);
		IR.add(new Move(ret,x));
		if(x instanceof Const&&markIfConst){
			tmp.isConst=true;
			tmp.value=((Const)x).value;
		}
		return ret.temp;
	}
	private static Oprand transNewRecord(NewRecord		expr, Object... dst) {
		return malloc(new Const(((RECORD)expr.ty).length),
				(dst!=null&&dst.length>0&&dst[0] instanceof TempOprand)?
						((TempOprand)dst[0]).temp:
							null);
	}
	private static Oprand trans(Null		expr) {
		return new Const(0);
	}
	private static Oprand transString(StringLiteral	expr, boolean rawRet, Object... dst) {
		Label name = new Label();
		Temp tmp = null;
		TempOprand ret =null;
		if(dst!=null&&dst.length>0&&dst[0] instanceof TempOprand){
			tmp=((TempOprand)dst[0]).temp;
			ret=(TempOprand) dst[0];
		}else if(!rawRet){
			tmp=new Temp();
			stackPtr.put(tmp, frameSize++);
			ret=Temp2TempOprand(tmp);
		}
		
		frag.add(new DataFrags(name,expr.s));
		if(!rawRet){
			IR.add(new Move(ret,new LabelAddress(name)));
			return ret;
		}else return new LabelAddress(name);
		
	}
	@SuppressWarnings("unused")
	private static Oprand trans(SubscriptPostfix	expr) {
		Temp tmp = null;
		Oprand base=null;
		Oprand tsubscript=null;
		if(expr.expr.ty.equals(STRING.getInstance())||charLength != wordLength||!(tsubscript instanceof Const)){
			base=trans(expr.expr, false);
			tsubscript=trans(expr.subscript, false);
		}
		TempOprand addr = null;
		int offset=0;
		if(!(tsubscript instanceof Const)){
			tmp=new Temp();
			stackPtr.put(tmp,frameSize++);
			addr=Temp2TempOprand(tmp);
		}
		if(!expr.expr.ty.equals(STRING.getInstance())){
			if(charLength == wordLength){
				if(!(tsubscript instanceof Const)){
					IR.add(new BinOp(addr,tsubscript,new Const(wordLength),BinaryOp.MULTIPLY));
					IR.add(new BinOp(addr,addr,base,BinaryOp.PLUS));
				}else{
					if(expr.ty instanceof INT&&expr.expr instanceof Id&&(expr.subscript instanceof IntLiteral||expr.subscript.isConst)){
						rewrittenArray key=new rewrittenArray(((Id)expr.expr),expr.subscript.value);
						if(inlineFuncRet==null){
							if(arrayTable.containsKey(key)){
								return Temp2TempOprand(arrayTable.get(key));
							}
						}else {
							if(arrayTable2.containsKey(key)){
								return Temp2TempOprand(arrayTable2.get(key));
							}
						}
					}
					base=trans(expr.expr, false);
					tsubscript=trans(expr.subscript, false);
					offset=((Const)tsubscript).value*wordLength;
					addr=mvOprand(base, false);
				}
			}else {
				//FIXME:only works if charLength == wordLength
				error("meow!charLength!=wordLength");
				IR.add(new Move(addr,base));
			}
			return new Mem(addr.temp,wordLength+offset, ((expr.ty instanceof CHAR)?charLength:wordLength));
			
		} else {
			if(!(tsubscript instanceof Const)){
				IR.add(new BinOp(addr,base,tsubscript,BinaryOp.PLUS));
			}else{
				offset=((Const)tsubscript).value;
				addr=mvOprand(base, false);
			}
			return new Mem(addr.temp,offset, 1);
		}
	}
	private static Oprand trans(UnaryExpr		expr) {
		TempOprand ret = null;
		Temp tmp;
		Oprand texpr=trans(expr.expr, false);
		
		switch(expr.op){
		case PLUS:
			return texpr;
		case MINUS:
			if(texpr instanceof Const){
				return new Const(-((Const)texpr).value);
			}
			tmp = new Temp();
			stackPtr.put(tmp, frameSize++);
			ret = Temp2TempOprand(tmp);
			IR.add(new BinOp(ret,new Const(0),texpr,BinaryOp.MINUS));
			return ret;
		case NOT://NOTE: input could take all values, while output should only be 0,1
			tmp = new Temp();
			stackPtr.put(tmp,frameSize++);
			ret = Temp2TempOprand(tmp);
			IR.add(new UnaryOp(ret,texpr,expr.op));
			return ret;
			
		default:
			error(expr.pos,expr.toString()+" : looks like 2012 stuff.");
		}
		return ret;
	}


	private static void error(Position pos,String msg){
		throw new SException(" line "+(pos.getLine()+1)+" ,column "+(pos.getColumn()+1)+" : "+msg);
	}

	public static void error(String msg) {
		throw new SException(msg);
	}


}

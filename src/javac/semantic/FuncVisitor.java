package javac.semantic;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import javac.env.Entry;
import javac.env.Env;
import javac.env.FuncEntry;
import javac.env.VarEntry;
import javac.absyn.*;
import javac.symbol.Symbol;
import javac.trans.rewrittenArray;
import javac.type.*;
import javac.util.Position;

public class FuncVisitor extends Semantic {
	Env globalEnv;
	Type returnType;
	int iteration;
	public static Symbol curFunc;
	public static boolean canInline;
	public static boolean canRewriteArray;
	public static HashMap<rewrittenArray,Integer> arrayCount;
	public static HashSet<String> baseTable;
	public static HashSet<String> killedTable;
	public static boolean hasBreakOrContinue;//this is not exact, but conservative

	public FuncVisitor(Env env) {
		super(env);
		globalEnv=env.cloneEnv();
		iteration=0;
	}

	public void strictType(Type l, Type r){
		if(!l.equals(r)){
			error("Strict types mismatched.");
		}
	}
	
	public boolean compatible(Type l, Type r){
		return (l instanceof STRING&&(r instanceof INT||r instanceof CHAR))||
				(r instanceof STRING&&(l instanceof INT||l instanceof CHAR))||
				((l instanceof INT||l instanceof CHAR)&&(r instanceof INT||r instanceof CHAR))||
				(l instanceof STRING&&r instanceof STRING);
	}

	public boolean ArrayOrRecord(Type l, Type r){
		return (l instanceof ARRAY|| l instanceof RECORD|| r instanceof ARRAY || r instanceof RECORD);
	}
	
	private static void error(Position pos,String msg){
		throw new SException(" line "+(pos.getLine()+1)+" ,column "+(pos.getColumn()+1)+" : "+msg);
	}

	private static void error(String msg) {
		throw new SException(msg);
		
	}
	
	@Override
	public void visit(ArrayType arrayType) {}

	@Override
	public void visit(BinaryExpr binaryExpr) {
		Type lty=binaryExpr.l.ty;
		Type rty=binaryExpr.r.ty;
		/*binaryExpr.isConst = (binaryExpr.l instanceof IntLiteral && binaryExpr.r instanceof IntLiteral)
				||(binaryExpr.l instanceof CharLiteral && binaryExpr.r instanceof CharLiteral)
				||(binaryExpr.l instanceof StringLiteral && binaryExpr.r instanceof StringLiteral)
				||(binaryExpr.l.isConst && binaryExpr.r.isConst);*/
		switch (binaryExpr.op){
		case MULTIPLY:
		case DIVIDE:
		case MODULO:
		case MINUS:
		case AND:
		case OR:
			if(lty instanceof INT && rty instanceof INT) binaryExpr.ty = INT.getInstance();
			else error(binaryExpr.pos,"binaryExpr INT mismatch");
			binaryExpr.lvalue=false;
			break;
		case PLUS:
			if(ArrayOrRecord(lty,rty))error(binaryExpr.pos,"binaryExpr + cannot be record or array.");
			else if(lty instanceof STRING || rty instanceof STRING) binaryExpr.ty = STRING.getInstance();
			else if(binaryExpr.inferType instanceof STRING)binaryExpr.ty = STRING.getInstance();
			else if((lty instanceof CHAR || lty instanceof INT) && (rty instanceof CHAR || rty instanceof INT)) binaryExpr.ty = INT.getInstance();
			else error(binaryExpr.pos,"binaryExpr INT mismatch");
			binaryExpr.lvalue=false;
			break;

		case LESS:
		case LESS_EQ:
		case GREATER:
		case GREATER_EQ:
			if(!ArrayOrRecord(lty,rty))binaryExpr.ty = INT.getInstance();
			else error(binaryExpr.pos,"binaryExpr INT COMP mismatch");
			binaryExpr.lvalue=false;
			break;
		case EQ:
		case NEQ:
			strictType(lty,rty);
			binaryExpr.ty = INT.getInstance();
			binaryExpr.lvalue=false;
			break;
		case ASSIGN:
			if(lty==null||rty==null)error(binaryExpr.pos,binaryExpr.toString());
			strictType(lty,rty);
			if(!binaryExpr.l.lvalue)error(binaryExpr.pos,"Non lvalue used as lvalue in assignment.");
			binaryExpr.ty = lty;
			binaryExpr.lvalue=false;//return value instead of address.
			break;
		case COMMA:
			binaryExpr.ty=rty;
			binaryExpr.lvalue=false;
			break;
		}
	}

	@Override
	public void visit(BreakStmt breakStmt) {
		hasBreakOrContinue=true;
		if(iteration==0)error(breakStmt.pos,"Continue stmt outside loop.");
		if(iteration<0)error(breakStmt.pos,"looks like 2012 stuff:"+breakStmt.toString());
	}

	@Override
	public void visit(CharLiteral charLiteral) {
		charLiteral.ty = charLiteral.inferType==null?CHAR.getInstance():charLiteral.inferType;
		charLiteral.lvalue = false;
	}

	@Override
	public void visit(CharType charType) {}

	@Override
	public void visit(CompoundStmt compoundStmt) {}

	@Override
	public void visit(ContinueStmt continueStmt) {
		hasBreakOrContinue=true;
		if(iteration==0)error(continueStmt.pos,"Continue stmt outside loop.");
		if(iteration<0)error(continueStmt.pos,"looks like 2012 stuff:"+continueStmt.toString());
	}

	@Override
	public void visit(ExprStmt exprStmt) { }

	@Override
	public void visit(FieldPostfix fieldPostfix) {
		Expr a = fieldPostfix.expr;
		Symbol b = fieldPostfix.field;

		if (a.ty instanceof STRING || a.ty.isArray()) {
			if (!b.equals(Symbol.valueOf("length")))error(fieldPostfix.pos,"field postfix must be length for string and arrays");
			fieldPostfix.ty = INT.getInstance();
		} else if (a.ty.isRecord()) {
			if (a.ty.isNull())error(fieldPostfix.pos,"NullPtrException(fake):fieldPostFix at a null");
			else {
				RECORD r = (RECORD) a.ty;
				if (!r.hasField(b))	error(fieldPostfix.pos,"No such field in record.");
				fieldPostfix.ty = r.getFieldType(b);
				fieldPostfix.lvalue=true;
			}
		}
	}


	@Override
	public void visit(ForStmt forStmt) {
		if(hasBreakOrContinue)forStmt.hasBreakOrContinue=hasBreakOrContinue;
		if(!(forStmt.cond.ty instanceof INT))error(forStmt.cond.pos,"condition code in ForStmt has to be int.");
		iteration--;
	}

	@Override
	public void visit(FunctionCall functionCall) {
		if(!(functionCall.expr instanceof Id))error(functionCall.expr.pos,"calling a nonfunction.");
		Entry e = env.get(((Id)functionCall.expr).sym);
		canRewriteArray=false;
		if(((Id)functionCall.expr).sym.equals(curFunc))
			canInline=false;
		if(!(e instanceof FuncEntry))error(functionCall.expr.pos,"calling a nonfunction.");
		FuncEntry fe = (FuncEntry) e;
		LinkedList<Expr> args = functionCall.args.exprList;
		List<ParameterDecl> pl = fe.head.parameterList.parameterDeclarations;
		Iterator<ParameterDecl> p = pl.iterator();
		Iterator<Expr> a = args.iterator();
		if(args.size()!=pl.size())error(functionCall.args.pos,"function args mismatched");
		for(;p.hasNext()&&a.hasNext();){
			if(!p.next().type.toType(env).equals(a.next().ty))error(functionCall.args.pos,"function args type mismatched");
		}
		functionCall.params = args;
		functionCall.ty = fe.head.type.toType(env);
		functionCall.lvalue = false;
	}

	@Override
	public void visit(FunctionDef functionDef) {
		if(returnType!=null && !returnType.equals(functionDef.head.type.toType(env)))error("function return type mismatched");
		returnType=null;
		env = globalEnv.cloneEnv();//return to global env in post-order visit.
		if(iteration!=0)error(functionDef.pos,"looks like 2012 stuff:"+functionDef.toString());
		functionDef.canInline=canInline;
		functionDef.canRewriteArray=canRewriteArray;
		if(canRewriteArray){
			functionDef.arrayCount=new HashMap<rewrittenArray,Integer>();
			for(rewrittenArray v:arrayCount.keySet()){
				if(arrayCount.get(v)<=2)continue;
				if(killedTable.contains(v.id.toString()))continue;
				functionDef.arrayCount.put(v,arrayCount.get(v));
			}
		}
	}

	@Override
	public void visit(FunctionHead functionHead) {
		functionHead.type.toType(env);//check whether this is a valid type
		curFunc=functionHead.functionName;
		canInline=true;
		canRewriteArray=true;
		arrayCount=new HashMap<rewrittenArray,Integer>();
		baseTable=new HashSet<String>();
		killedTable=new HashSet<String>();
		hasBreakOrContinue=false;
	}

	@Override
	public void visit(Id id) {
		Entry e = env.get(id.sym);
		if (e instanceof VarEntry) {
			VarEntry ve = (VarEntry) e;
			id.ty = id.inferType==null||!compatible(id.inferType,ve.type)?ve.type:id.inferType;
			id.lvalue=true;
		} else if (e instanceof FuncEntry) {
			FuncEntry fe = (FuncEntry) e;
			id.ty = fe.head.type.toType(env);
			id.lvalue=false;
			//TOOD:how to determine lvalue?f()=1 and f()[0]=1
		} else {
			error(id.pos,"This is not a valid ID.");
		}
	}

	@Override
	public void visit(IdList idList) {}

	@Override
	public void visit(IdType idType) {}

	@Override
	public void visit(IfStmt ifStmt) {
		if(!(ifStmt.cond.ty instanceof INT))error(ifStmt.cond.pos,"condition code in IF not INT.");
	}

	@Override
	public void visit(IntLiteral intLiteral) {
		intLiteral.ty = INT.getInstance();
		intLiteral.lvalue = false;
	}

	@Override
	public void visit(IntType intType) {}

	@Override
	public void visit(NewArray newArray) {
		newArray.ty = new ARRAY(newArray.type.toType(env));
		newArray.lvalue= false;
	}

	@Override
	public void visit(NewRecord newRecord) {
		if(!(newRecord.type instanceof IdType))error(newRecord.pos,"Only ID could be new-ed.");
		newRecord.ty = ((IdType)newRecord.type).toType(env);
		newRecord.lvalue=false;
	}

	@Override
	public void visit(Null n) {
		n.ty=Type.NULL;
		n.lvalue=false;
	}

	@Override
	public void visit(ParameterDecl parameterDecl) {
		parameterDecl.type.toType(env);
	}

	@Override
	public void visit(ParameterList parameterList) {
		//This is in function head, and is visited first.
		for (ParameterDecl decl: parameterList.parameterDeclarations)
			env.put(decl.name, new VarEntry(decl.name, decl.type.toType(env)));
	}

	@Override
	public void visit(PrototypeDecl prototypeDecl) {
		env=globalEnv.cloneEnv();
		if(iteration!=0)error(prototypeDecl.pos,"looks like 2012 stuff:"+prototypeDecl.toString());
	}

	@Override
	public void visit(RecordDef recordDef) {}

	@Override
	public void visit(ReturnStmt returnStmt) {
		if(returnType != null && !returnType.equals(returnStmt.expr.ty))error(returnStmt.pos,"function return type mismatched");
		returnType = returnStmt.expr.ty;
	}

	@Override
	public void visit(StmtList stmtList) {}

	@Override
	public void visit(StringLiteral stringLiteral) {
		stringLiteral.ty = STRING.getInstance();
		stringLiteral.lvalue = false;
	}

	@Override
	public void visit(StringType stringType) {}

	@Override
	public void visit(SubscriptPostfix subscriptPostfix) {
		if (!subscriptPostfix.expr.ty.equals(STRING.getInstance())&&!subscriptPostfix.expr.ty.isArray())error(subscriptPostfix.expr.pos,"subscriptPostfix not array or string");
		if (!subscriptPostfix.subscript.ty.equals(INT.getInstance()))error(subscriptPostfix.subscript.pos,"SubscriptPostfix expr must be a int");
		subscriptPostfix.ty = subscriptPostfix.expr.ty.isArray() ? ((ARRAY) subscriptPostfix.expr.ty).elementType:CHAR.getInstance();
		subscriptPostfix.lvalue = subscriptPostfix.expr.lvalue;
		if(canRewriteArray&&subscriptPostfix.ty instanceof INT&&subscriptPostfix.expr instanceof Id&&(subscriptPostfix.subscript instanceof IntLiteral||subscriptPostfix.subscript.isConst)){
			rewrittenArray key=new rewrittenArray(((Id)subscriptPostfix.expr),subscriptPostfix.subscript.value);
			if(arrayCount.containsKey(key)){
				arrayCount.put(key, arrayCount.get(key)+1);
				baseTable.add(((Id)subscriptPostfix.expr).toString());
			}else{
				arrayCount.put(key, 1);
				baseTable.add(((Id)subscriptPostfix.expr).toString());
			}
		}else{
			if(subscriptPostfix.expr instanceof Id&&baseTable.contains(((Id)subscriptPostfix.expr).toString())){
				killedTable.add(((Id)subscriptPostfix.expr).toString());
			}
		}
	}

	@Override
	public void visit(TranslationUnit translationUnit) {}

	@Override
	public void visit(UnaryExpr unaryExpr) {
		if(!(unaryExpr.expr.ty instanceof INT))error(unaryExpr.pos,"UnaryOp on expr not INT");
		unaryExpr.ty = INT.getInstance();
		unaryExpr.lvalue=false;
	}

	@Override
	public void visit(VariableDecl variableDecl) {
		if (variableDecl.inRecord)return;
		Type type = variableDecl.type.toType(env);
		for (Symbol s : variableDecl.ids.ids)env.put(s, new VarEntry(s, type));
	}

	@Override
	public void visit(VariableDeclList variableDeclList) {
	}

	@Override
	public void visit(WhileStmt whileStmt) {
		if(!(whileStmt.cond.ty instanceof INT))error(whileStmt.cond.pos,"condition code in while stmt not INT");
		iteration--;
	}
	@Override
	public void previsit(ForStmt forStmt) {
		iteration++;
		hasBreakOrContinue=false;
	}

	@Override
	public void previsit(WhileStmt whileStmt) {
		iteration++;
	}

}

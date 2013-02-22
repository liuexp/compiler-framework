/* Empty overriding visitor*/
package javac.semantic;

import javac.absyn.*;
import javac.env.Env;

public class Semantic implements NodeVisitor {

	protected Env env;

	public Semantic(Env env) {
		this.env = env;
	}

	@Override
	public void visit(ArrayType arrayType) {}

	@Override
	public void visit(BinaryExpr binaryExpr) {}

	@Override
	public void visit(BreakStmt breakStmt) {}

	@Override
	public void visit(CharLiteral charLiteral) {}

	@Override
	public void visit(CharType charType) {}

	@Override
	public void visit(CompoundStmt compoundStmt) {}

	@Override
	public void visit(ContinueStmt continueStmt) {}

	@Override
	public void visit(ExprStmt exprStmt) {}

	@Override
	public void visit(FieldPostfix fieldPostfix) {}

	@Override
	public void visit(ForStmt forStmt) {}

	@Override
	public void visit(FunctionCall functionCall) {}

	@Override
	public void visit(FunctionDef functionDef) {}

	@Override
	public void visit(FunctionHead functionHead) {}

	@Override
	public void visit(Id id) {}

	@Override
	public void visit(IdList idList) {}

	@Override
	public void visit(IdType idType) {}

	@Override
	public void visit(IfStmt ifStmt) {}

	@Override
	public void visit(IntLiteral intLiteral) {}

	@Override
	public void visit(IntType intType) {}

	@Override
	public void visit(NewArray newArray) {}

	@Override
	public void visit(NewRecord newRecord) {}

	@Override
	public void visit(Null n) {}

	@Override
	public void visit(ParameterDecl parameterDecl) {}

	@Override
	public void visit(ParameterList parameterList) {}

	@Override
	public void visit(PrototypeDecl prototypeDecl) {}

	@Override
	public void visit(RecordDef recordDef) {}

	@Override
	public void visit(ReturnStmt returnStmt) {}

	@Override
	public void visit(StmtList stmtList) {}

	@Override
	public void visit(StringLiteral stringLiteral) {}

	@Override
	public void visit(StringType stringType) {}

	@Override
	public void visit(SubscriptPostfix subscriptPostfix) {}

	@Override
	public void visit(TranslationUnit translationUnit) {}

	@Override
	public void visit(UnaryExpr unaryExpr) {}

	@Override
	public void visit(VariableDecl variableDecl) {}

	@Override
	public void visit(VariableDeclList variableDeclList) {}

	@Override
	public void visit(WhileStmt whileStmt) {}

	public Env getEnv() {
		return env;
	}

	@Override
	public void visit(ArgsList argsList) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void previsit(ForStmt forStmt) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void previsit(WhileStmt whileStmt) {
		// TODO Auto-generated method stub
		
	}
}

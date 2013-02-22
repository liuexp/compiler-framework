package javac.semantic;

import javac.absyn.RecordDef;
import javac.absyn.VariableDecl;
import javac.absyn.VariableDeclList;
import javac.env.Env;
import javac.env.TypeEntry;
import javac.symbol.Symbol;
import javac.type.CHAR;
import javac.type.RECORD;

public class RecordVisitor extends Semantic {

	public RecordVisitor(Env env) {
		super(env);
		// TODO Auto-generated constructor stub
	}
	@Override
	public void visit(RecordDef recordDef) {
		final TypeEntry e= TypeEntry.transferTypeEntry(env.get(recordDef.name));
		RECORD r = (RECORD) e.type;
		VariableDeclList f = recordDef.fields;
		int c=0;
		for (VariableDecl dec : f.variableDeclarations) {
			dec.inRecord = true;
			for(Symbol n: dec.ids.ids){
				r.add(n,(new RECORD.RecordField(dec.type.toType(env), n, c)));
				if(dec.type.toType(env) instanceof CHAR) c+=javac.trans.Trans.charLength;
				else c+=javac.trans.Trans.wordLength;
			}
		}
		r.length=c;
	}


}

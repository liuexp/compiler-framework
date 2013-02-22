package javac.quad;
import java.util.HashSet;

import javac.absyn.BinaryOp;
import javac.semantic.SException;

public class BinOp extends Quad {

	public Oprand left;
	public Oprand right;
	public Oprand dst;
	public BinaryOp op;
	@Override
	public String toString() {
		return dst.toString() + "<-" + left.toString() + trans(op) +right.toString();
	}

	public BinOp(Oprand d, Oprand l, Oprand r, BinaryOp o) {
		dst = d;
		left = l;
		right = r;
		op = o;
		use=new HashSet<Temp>();
		def=new HashSet<Temp>();
		if(l instanceof TempOprand&&!((TempOprand)l).temp.isOnceTemp)use.add(((TempOprand)l).temp);
		else if(l instanceof Mem)use.add(((Mem)l).base);
		if(r instanceof TempOprand&&!((TempOprand)r).temp.isOnceTemp)use.add(((TempOprand)r).temp);
		else if(r instanceof Mem)use.add(((Mem)r).base);
		if(d instanceof TempOprand&&!((TempOprand)d).temp.isOnceTemp)def.add(((TempOprand)d).temp);
		else if(d instanceof Mem)def.add(((Mem)d).base);
	}

	public String getAsm(BinaryOp o) { 
		switch(op) {
		case PLUS: 
			if(left instanceof Const||right instanceof Const)
				return "add";
			else return "addu";
		case MINUS: 
			return "subu";
		case MULTIPLY:
			if(canFoldByShift())return "sll";
			return "mul";
		case DIVIDE:
			if(canFoldByShift())return "sra";
			return "div";
		case MODULO:
			return "rem";
		case LESS:
			return "slt";
		case LESS_EQ:
			return "sle";
		case GREATER:
			return "sgt";
		case GREATER_EQ:
			return "sge";
		case EQ:
			return "seq";
		case NEQ:
			return "sne";
		case AND:
			return "and";
		case OR:
			return "or";
		default:
			throw new SException("BinOp 2012!");
		}
	}
	static String trans(BinaryOp o) { 
		switch(o) {
		case PLUS: 
			return "+";
		case MINUS: 
			return "-";
		case MULTIPLY:
			return "*";
		case DIVIDE:
			return "/";
		case MODULO:
			return "%";
		case LESS:
			return "<";
		case LESS_EQ:
			return "<=";
		case GREATER:
			return ">";
		case GREATER_EQ:
			return ">=";
		case EQ:
			return "=";
		case NEQ:
			return "!=";
		case AND:
			return "&&";
		case OR:
			return "||";
		default:
			return "error";
		}
	}

	@Override
	public String toAsm() {
		//return getAsm(op)+ dst.toString() + "," + left.toString() +"," +right.toString();
		String leftR="$t0",rightR="$t1",dstR="$t2";
		String opcode=getAsm(op);
		
		if(left instanceof Const&&op==BinaryOp.PLUS){
			if(right instanceof Const)leftR=Integer.toString(((Const)left).value);
			else {
				leftR=rightR;
				rightR=Integer.toString(((Const)left).value);
			}
		}
		if(right instanceof Const&&op==BinaryOp.PLUS){
			rightR=Integer.toString(((Const)right).value);
		}
		if(right instanceof Const&&op==BinaryOp.MINUS){
			rightR=Integer.toString(-((Const)right).value);
			opcode="addiu";
		}
		
		if(canFoldByShift())
			rightR=Integer.toString(log2(((Const)right).value));
				
		return opcode + " "+dstR+" , "+leftR+", "+rightR;
	}
	
	public boolean canFoldByShift(){
		if(op==BinaryOp.MULTIPLY||op==BinaryOp.DIVIDE){
			if(right instanceof Const){
				int rv=((Const)right).value;
				if(rv!=0&&(rv&(rv-1))==0){
					return true;
				}
			}
		}
		return false;
	}

	public static int log2(int rv) {
		int ret=0;
		while(rv>1){
			rv>>=1;
			ret++;
		}
		return ret;
	}
}

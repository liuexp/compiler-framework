package javac.quad;

import java.util.HashSet;

import javac.absyn.BinaryOp;
import javac.semantic.SException;

public class Branch extends Quad {
	public Branch(){
		
	}
	public Branch(Label la, Oprand l, Oprand r, BinaryOp o) {
		label = la;
		left = l;
		right = r;
		op = o;
		if(o==BinaryOp.GREATER){//bgt is expensive
			left=r;
			right=l;
			op=BinaryOp.LESS;
		}
		use=new HashSet<Temp>();
		def=new HashSet<Temp>();
		if(l instanceof TempOprand&&!((TempOprand)l).temp.isOnceTemp)use.add(((TempOprand)l).temp);
		else if(l instanceof Mem)use.add(((Mem)l).base);
		if(r instanceof TempOprand&&!((TempOprand)r).temp.isOnceTemp)use.add(((TempOprand)r).temp);
		else if(r instanceof Mem)use.add(((Mem)r).base);
	}
	public Oprand left;
	public Oprand right;
	public BinaryOp op;
	public Label label;
	@Override
	public String toString() {
		return left.toString() + BinOp.trans(op) + right.toString() + "? goto " + label.toString();
	}
	
	public String getAsm() { 
		String opcode="";
		switch(op){
		case EQ:
			opcode = "beq";
			break;
		case NEQ:
			opcode = "bne";
			break;
		case LESS:
			opcode = "blt";
			break;
		case GREATER:
			opcode = "bgt";
			break;
		case LESS_EQ:
			opcode = "ble";
			break;
		case GREATER_EQ:
			opcode = "bge";
			break;
			default:
				error("2012");
		}
		return opcode;
	}

	@Override
	public String toAsm() {
		
		String opcode=getAsm();
		
		//return opcode  + left.toString() +"," +right.toString()+ ","+label.toString();
		String leftR="$t0",rightR="$t1";
		if(left instanceof Const&&((Const)left).value==0){
			//leftR=Integer.toString(((Const)left).value);
			leftR="$zero";
		}
		if(right instanceof Const&&((Const)right).value==0){
			rightR="$zero";
		}
		return opcode + " "+leftR+","+rightR+","+label.toString();
	}
	private static void error(String msg) {
		throw new SException(msg);
	}
}

package javac.quad;

import java.util.HashSet;

import javac.trans.Trans;

public class Move extends Quad {
	
	public Oprand src;
	public Oprand dst;
	public Move(Oprand d, Oprand s) {
		if(d==null||s==null)Trans.error("mie!Move!new");
		dst = d;
		src = s;
		use=new HashSet<Temp>();
		def=new HashSet<Temp>();
		if(s instanceof TempOprand&&!((TempOprand)s).temp.isOnceTemp)use.add(((TempOprand)s).temp);
		else if(s instanceof Mem)use.add(((Mem)s).base);
		if(d instanceof TempOprand&&!((TempOprand)d).temp.isOnceTemp)def.add(((TempOprand)d).temp);
		else if(d instanceof Mem)use.add(((Mem)d).base);
	}
	@Override
	public String toString() {
		return dst.toString() + " <- " + src.toString();
	}
	@Override
	public String toAsm() {
		return "";
	}
}

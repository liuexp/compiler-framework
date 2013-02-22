package javac.quad;

import java.util.HashSet;

public class Return extends Quad {

	public Oprand value;
	public boolean isFuncExit;
	public Return(Oprand v){
		value = v;
		isFuncExit=false;
		if(v instanceof TempOprand&&!((TempOprand)v).temp.isOnceTemp){
			use=new HashSet<Temp>();
			use.add(((TempOprand)v).temp);
		}else if(v instanceof Mem){
			use=new HashSet<Temp>();
			use.add(((Mem)v).base);
		}
		
	}

	@Override
	public String toString() {
		return "return " + value.toString();
	}
	@Override
	public String toAsm() {
		return "";
	}
}

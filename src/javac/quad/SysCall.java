package javac.quad;

import java.util.HashSet;
//FIXME:this file is deprecated

public class SysCall extends Quad {

	public SysCall(Label name, Oprand[] p, Temp r) {
		function = name;
		params = p;
		result = r;
		killedret=false;
		hasArrayOrRecord=false;
		use=new HashSet<Temp>();
		def=new HashSet<Temp>();
		for(Oprand pp:p){
			if(!(pp instanceof TempOprand))continue;
			use.add(((TempOprand)pp).temp);
		}
		if(r!=null&&!r.isOnceTemp)def.add(r);
	}
	public SysCall(Label name, Oprand[] p, Temp r,boolean hasArrayOrRecord) {
		function = name;
		params = p;
		result = r;
		killedret=false;
		this.hasArrayOrRecord=hasArrayOrRecord;
		use=new HashSet<Temp>();
		def=new HashSet<Temp>();
		for(Oprand pp:p){
			if(!(pp instanceof TempOprand))continue;
			use.add(((TempOprand)pp).temp);
		}
		if(r!=null&&!r.isOnceTemp)def.add(r);
	}
	public Label function;
	public Oprand[] params;
	public Temp result;
	public boolean killedret;
	public boolean hasArrayOrRecord;
	@Override
	public String toString() {
		String call = (result==null?"null":result.toString()) + "<-" + function + "(";
		if (params != null)
			for (int i = 0; i < params.length; i++) {
				call += params[i].toString() + ',';
			}
		call += ")";
		return call;
	}
	@Override
	public String toAsm() {
		return "jal "+(function.toString().equals("main")?"":"_")+function;
	}

}

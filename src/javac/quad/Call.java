package javac.quad;

import java.util.HashSet;

import javac.trans.FuncFrag;

public class Call extends Quad {

	public Call(Label name, Temp[] p, Temp r) {
		function = name;
		params = p;
		result = r;
		killedret=false;
		hasArrayOrRecord=false;
		use=new HashSet<Temp>();
		def=new HashSet<Temp>();
		for(Temp pp:p){
			if(pp.isOnceTemp)continue;
			use.add(pp);
		}
		if(r!=null&&!r.isOnceTemp)def.add(r);
	}
	public Call(Label name, Temp[] p, Temp r,boolean hasArrayOrRecord) {
		function = name;
		params = p;
		result = r;
		killedret=false;
		this.hasArrayOrRecord=hasArrayOrRecord;
		use=new HashSet<Temp>();
		def=new HashSet<Temp>();
		for(Temp pp:p){
			if(pp.isOnceTemp)continue;
			use.add(pp);
		}
		if(r!=null&&!r.isOnceTemp)def.add(r);
	}
	public Label function;
	public Temp[] params;
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

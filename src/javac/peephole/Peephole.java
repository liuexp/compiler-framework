package javac.peephole;
import java.util.LinkedList;

import javac.quad.*;
public class Peephole {
	public static boolean enabled=false;
	//FIXME:skipped instructions?
	public static String RedundantLWSW(String in){
		String[] str=in.split("\n");
		StringBuffer ret=new StringBuffer();
		for(int i=0;i<str.length-1;i++){
			if(str[i].length()<2)continue;
			if(str[i].substring(0, 1)	=="lw"&&str[i+1].substring(0, 1)	=="sw"){
				String[] str1=str[i].substring(2).split(",");
				String[] str2=str[i+1].substring(2).split(",");
				if(str[0]==str2[0]&&str1[1]==str2[1]){
					i++;
				}else{
					ret.append(str[i]+"\n");
				}
			}else{
				ret.append(str[i]+"\n");
			}
		}
		ret.append(str[str.length-1]+"\n");
		return ret.toString();
	}

	public static LinkedList<Quad> peephole(LinkedList<Quad> instr) {
		if(!enabled)return instr;
		else return RelabelAndMark(instr);
	}

	public static LinkedList<Quad> RelabelAndMark(LinkedList<Quad> instr) {
		// TODO Auto-generated method stub
		return null;
	}
}

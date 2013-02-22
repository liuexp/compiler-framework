/*
 * The Actual Procedure is inlined into FuncFrag
 */
package javac.linearScan;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.PriorityQueue;
import java.util.TreeSet;

import javac.block.BasicBlock;
import javac.quad.*;

public class LinearScan {
	public final static String[] regs={//"$zero", "$at",  
			"$t0", "$t1",
			"$t2", "$t3", "$t4", "$t5", "$t6", "$t7",
			"$s0", "$s1", "$s2", "$s3", "$s4", "$s5",
			"$s6", "$s7", "$t8", "$t9","$fp","$gp", "$a2", "$a3", "$ra","$v1","$a1","$a0","$v0"
			//, "$sp", "$k0", "$k1"
			};
	
	
	public static HashMap<Interval,String> getRegMap(BasicBlock b){
		int R=regs.length;
		
		LinkedList<String> freeRegs=new LinkedList<String>(Arrays.asList(regs));
		HashMap<Interval,String> regMap=new HashMap<Interval,String>();
		if(b.instr.size()<1)return regMap;
		PriorityQueue<Interval> Qs=new PriorityQueue<Interval>(b.instr.size()+1,new IntCompS());
		TreeSet<Interval> active=new TreeSet<Interval>(new IntCompE());
		HashMap<Interval,Integer> framePtr=new HashMap<Interval,Integer> ();
		int frameSize=0;
		Qs.addAll(b.liveRange.values());
		while(!Qs.isEmpty()){
			Interval i=Qs.poll();
			while(!active.isEmpty()){
				Interval j = active.first();
				if(j.endpoint>=i.startpoint)break;
				active.pollFirst();
				freeRegs.add(regMap.get(j));
			}
			if(active.size()==R){
				Interval spill=active.last();
				if(spill.endpoint>i.endpoint){
					regMap.put(i, regMap.get(spill));
					framePtr.put(spill, frameSize++);
					active.pollLast();
					active.add(i);
				}else{
					framePtr.put(i, frameSize++);
				}
			}else{
				regMap.put(i, freeRegs.poll());
			}
		}
		b.framePtr=framePtr;
		b.frameSize=frameSize;
		return regMap;
	}

}

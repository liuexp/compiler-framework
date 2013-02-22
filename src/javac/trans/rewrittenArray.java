package javac.trans;

import javac.absyn.Id;

public class rewrittenArray {
	public Id id;
	public int offset;
	public rewrittenArray(Id i,int o){
		id=i;
		offset=o;
	}
	@Override
	public String toString() {
		return id + "[" + offset + "]";
	}
	@Override
	public int hashCode() {
		return toString().hashCode();
	}
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		rewrittenArray other = (rewrittenArray) obj;
		if (id == null) {
			if (other.id != null)
				return false;
		} else if (!id.toString().equals(other.id.toString()))
			return false;
		if (offset != other.offset)
			return false;
		return true;
	}
	
}

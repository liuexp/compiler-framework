package javac.type;

import java.util.HashMap;
import java.util.Map;

import javac.semantic.SException;
import javac.symbol.Symbol;

public final class RECORD extends Type {

	public static final class RecordField {

		public Type type;
		public Symbol name;
		public int index;

		public RecordField(Type type, Symbol name, int index) {
			this.type = type;
			this.name = name;
			this.index = index;
		}
	}

	//public List<RecordField> fields;
	public Map<Symbol,RecordField> fields;
	public Symbol name;
	public int length;// whole bit length to assign
	
	public void add(Symbol n,RecordField r){
		if(fields.containsKey(n))throw new SException("Duplicate names in Record");
		fields.put(n,r);
	}

	public RECORD(Symbol name) {
		fields = new HashMap<Symbol,RecordField>();
		this.name = name;
	}

	@Override
	public boolean equals(Object other) {
		if (other instanceof RECORD) {
			return name.equals(((RECORD) other).name)||this.isNull()||((RECORD)other).isNull();
		}
		return false;
	}

	@Override
	public boolean isArray() {
		return false;
	}

	@Override
	public boolean isRecord() {
		return true;
	}

	public boolean hasField(Symbol b) {
		// TODO Auto-generated method stub
		return fields.containsKey(b);
		
	}

	public Type getFieldType(Symbol b) {
		// TODO Auto-generated method stub
		return fields.get(b).type;
	}

}

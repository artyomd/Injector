package app.artyomd.injector;

class TextSymbolItem {
	private String type;
	private String clazz;
	private String name;
	private String value;

	TextSymbolItem(String type, String clazz, String name, String value) {
		this.type = type;
		this.clazz = clazz;
		this.name = name;
		this.value = value;
	}

	String getType() {
		return type;
	}

	String getClazz() {
		return clazz;
	}

	String getName() {
		return name;
	}

	String getValue() {
		return value;
	}
}
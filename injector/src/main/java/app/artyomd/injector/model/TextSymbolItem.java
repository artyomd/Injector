package app.artyomd.injector.model;

public class TextSymbolItem {
	private String type;
	private String clazz;
	private String name;
	private String value;

	public TextSymbolItem(String type, String clazz, String name, String value) {
		this.type = type;
		this.clazz = clazz;
		this.name = name;
		this.value = value;
	}

	public String getType() {
		return type;
	}

	public String getClazz() {
		return clazz;
	}

	public String getName() {
		return name;
	}

	public String getValue() {
		return value;
	}
}
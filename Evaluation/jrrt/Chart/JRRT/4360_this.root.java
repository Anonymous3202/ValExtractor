package org.jfree.data.xml;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class ItemHandler extends DefaultHandler implements DatasetTags  {
  private RootHandler root;
  private DefaultHandler parent;
  private Comparable key;
  private Number value;
  public ItemHandler(RootHandler root, DefaultHandler parent) {
    super();
    this.root = root;
    this.parent = parent;
    this.key = null;
    this.value = null;
  }
  public Comparable getKey() {
    return this.key;
  }
  public Number getValue() {
    return this.value;
  }
  public void endElement(String namespaceURI, String localName, String qName) {
    if(this.parent instanceof PieDatasetHandler) {
      PieDatasetHandler handler = (PieDatasetHandler)this.parent;
      handler.addItem(this.key, this.value);
      RootHandler var_4360 = this.root;
      var_4360.popSubHandler();
    }
    else 
      if(this.parent instanceof CategorySeriesHandler) {
        CategorySeriesHandler handler = (CategorySeriesHandler)this.parent;
        handler.addItem(this.key, this.value);
        this.root.popSubHandler();
      }
  }
  public void setKey(Comparable key) {
    this.key = key;
  }
  public void setValue(Number value) {
    this.value = value;
  }
  public void startElement(String namespaceURI, String localName, String qName, Attributes atts) throws SAXException {
    if(qName.equals(ITEM_TAG)) {
      KeyHandler subhandler = new KeyHandler(this.root, this);
      this.root.pushSubHandler(subhandler);
    }
    else 
      if(qName.equals(VALUE_TAG)) {
        ValueHandler subhandler = new ValueHandler(this.root, this);
        this.root.pushSubHandler(subhandler);
      }
      else {
        throw new SAXException("Expected <Item> or <Value>...found " + qName);
      }
  }
}
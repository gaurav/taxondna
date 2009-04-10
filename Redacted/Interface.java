
/**
 * SequenceInfo is an entire map for information about
 * sequences. It's quite delicious: since there's SO
 * much information here, we should come up with all
 * kinds of fantastic ways to skimp on disk space.
 */

interface SequenceInfo implements Map {
	private Sequence seq = null;
	private Hashtable hash 	= new Hashtable();
	private Vector features = new Vector();

	/**
	 * @param seq The Sequence we will be associated with.
	 *
	 * TODO: Does this work? According to [http://java.sun.com/docs/books/tutorial/java/javaOO/accesscontrol.html],
	 * this SHOULD only allow DNA.* objects to create SequenceInfo objects, which would be very nice.
	 */
	SequenceInfo(Sequence seq) {
		// and do you, SequenceInfo, take this sequence to be your lawfully wedded Seq?
		// (note that Sequence should set this on the other end)
		this.seq = seq;
	}

	/**
	 * Checks to see whether the key is valid.
	 */
	private String keyGuard(Object o) {
		String key = (String) o;
	}

	/** 
	 * Returns the size of the SequenceInfo.
	 * It's only really here because of 'Map': you probably
	 * wouldn't care to know.
	 */
	public int size() {
		return	hash.size();
	}

	/**
	 * Clears all the SequenceInfo stored here.
	 */
	public void clear() {
		hash.clear();
	}

	/**
	 * Checks whether we have this key.
	 */
	public boolean	containsKey(Object key) {
		return hash.containsKey(keyGuard(key));
	}

	/**
	 * Checks whether we have this value.
	 * I have no idea why you'd need this, either.
	 */
	public boolean containsValue(Object value) {
		return hash.containsValue(value);
	}

	/**
	 * Returns the set of entries. Why? Why is the sky blue?
	 */
	public Set entrySet() {
		return hash.entrySet();
	}
	
	/**
	 * Returns whether we are equal to another object.
	 */
	public boolean equals(Object o) {
		if(o.getClass().equals(this.getClass())) {
			// same class
			SequenceInfo info = (SequenceInfo) o;

			if(info.entrySet().equals(entrySet()))
				return true;

			return false;
		}

		return false;
	}
	
	/**
	 * Returns this entry.
	 */
	public Object get(Object key) {
		return hash.get(keyGuard(key));
	}
	
	/**
	 * Returns the hashcode of the underlying hash.
	 */
	public int hashCode() {
		return hash.hashCode();
	}

	/**
	 * Returns whether the map is empty or not.
	 */
	public boolean isEmpty() {
		return hash.isEmpty();
	}

	/**
	 * Returns the keySet. You might actually need this!
	 */
	public Set keySet() {
		return hash.keySet();
	}

	/**
	 * Sets a value. I think it returns the old value.
	 */
	public Object put(Object key, Object value) {
		return hash.put(keyGuard(key), value);
	}

	/**
	 * Puts this whole map into this thingie.
	 * I have no idea how this works, and we might
	 * have to overload this if we do funky things
	 * with the 'put'.
	 */
	public void putAll(Map t) {
		hash.putAll(t);
	}
	
	/**
	 * Removes a 'key'.
	 */
	public Object remove(Object key) {
		return hash.remove(keyGuard(key));
	}
	
	/**
	 * Returns a collection of values.
	 * Also needs overloading if we do funky stuff
	 * with the hash.
	 */
	public Collection values() {
		return hash.values();
	}

	/**
	 * Returns an iterator over all features.
	 */
	public Iterator getFeatureIterator() {
		return features.iterator();	
	}

	/**
	 * Adds a feature to this sequence.
	 */
	public void addFeature(Feature f) {
		features.add(f);
	}
	
	/**
	 * Removes a feature to this sequence.
	 */
	public void removeFeature(Feature f) {
		features.remove(f);
	}
}

public class Feature extends SequenceInfo {
	Sequence seq = null;
	int from = 0;
	int to = 0;

	public Feature(Sequence seq, int from, int to) {
		this.seq = seq;
		this.from = from;
		this.to = to;
	}
	
	public Sequence getSequence() {
		return seq;
	}

	public Sequence getSubsequence() {
		return seq.subSequence(from, to);
	}
}

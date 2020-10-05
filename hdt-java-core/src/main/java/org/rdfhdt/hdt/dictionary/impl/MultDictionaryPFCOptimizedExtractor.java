package org.rdfhdt.hdt.dictionary.impl;

import org.rdfhdt.hdt.dictionary.DictionarySectionPrivate;
import org.rdfhdt.hdt.dictionary.impl.section.PFCDictionarySectionMap;
import org.rdfhdt.hdt.dictionary.impl.section.PFCOptimizedExtractor;
import org.rdfhdt.hdt.enums.TripleComponentRole;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class MultDictionaryPFCOptimizedExtractor implements OptimizedExtractor{
	private final PFCOptimizedExtractor shared, subjects, predicates;
	private final HashMap<String,PFCOptimizedExtractor> objects;
	private final long numshared;

	public MultDictionaryPFCOptimizedExtractor(MultipleSectionDictionary origDict) {
		numshared=(int) origDict.getNshared();
		shared = new PFCOptimizedExtractor((PFCDictionarySectionMap) origDict.shared);
		subjects = new PFCOptimizedExtractor((PFCDictionarySectionMap) origDict.subjects);
		predicates = new PFCOptimizedExtractor((PFCDictionarySectionMap) origDict.predicates);
		objects = new HashMap<>();
		Iterator iterator = origDict.getAllObjects().entrySet().iterator();
		while (iterator.hasNext()){
			Map.Entry entry = (Map.Entry)iterator.next();
			objects.put((String)entry.getKey(),new PFCOptimizedExtractor((PFCDictionarySectionMap)entry.getValue()));
		}
	}

	@Override
	public CharSequence idToString(long id, TripleComponentRole role) {
		PFCOptimizedExtractor section = getSection(id, role);
		long localId = getLocalId(id, role);
		return section.extract(localId);
	}
	
	private PFCOptimizedExtractor getSection(long id, TripleComponentRole role) {
		switch (role) {
		case SUBJECT:
			if(id<=numshared) {
				return shared;
			} else {
				return subjects;
			}
		case PREDICATE:
			return predicates;
		case OBJECT:
			if(id<= numshared) {
			return shared;
		} else {
			Iterator hmIterator = objects.entrySet().iterator();
			// iterate over all subsections in the objects section
			PFCOptimizedExtractor desiredSection = null;
			int count = 0;
			while (hmIterator.hasNext()){
				Map.Entry entry = (Map.Entry)hmIterator.next();
				PFCOptimizedExtractor subSection = (PFCOptimizedExtractor)entry.getValue();
				count+= subSection.getNumStrings();
				if(id <= numshared+count){
					desiredSection = subSection;
					break;
				}
			}
			return desiredSection;
		}
		}
		throw new IllegalArgumentException();
	}

	
	private long getLocalId(long id, TripleComponentRole position) {
		switch (position) {
		case SUBJECT:
			if(id <= numshared)
				return id;
			else
				return id - numshared;
		case OBJECT:
			if(id<=numshared) {
				return id;
			} else {
				Iterator hmIterator = objects.entrySet().iterator();
				// iterate over all subsections in the objects section
				long count = 0;
				while (hmIterator.hasNext()){
					Map.Entry entry = (Map.Entry)hmIterator.next();
					PFCOptimizedExtractor subSection = (PFCOptimizedExtractor)entry.getValue();
					count+= subSection.getNumStrings();
					if(id <= numshared+ count){
						count -= subSection.getNumStrings();
						break;
					}
				}
				// subtract the number of elements in the shared + the subsections in the objects section
				return id - count - numshared;
			}
		case PREDICATE:
			return id;
		}

		throw new IllegalArgumentException();
	}
}

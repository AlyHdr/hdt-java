/*
 * File: $HeadURL: https://hdt-java.googlecode.com/svn/trunk/hdt-java/src/org/rdfhdt/hdt/dictionary/impl/HashDictionary.java $
 * Revision: $Rev: 191 $
 * Last modified: $Date: 2013-03-03 11:41:43 +0000 (dom, 03 mar 2013) $
 * Last modified by: $Author: mario.arias $
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation;
 * version 3.0 of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 * Contacting the authors:
 *   Mario Arias:               mario.arias@deri.org
 *   Javier D. Fernandez:       jfergar@infor.uva.es
 *   Miguel A. Martinez-Prieto: migumar2@infor.uva.es
 *   Alejandro Andres:          fuzzy.alej@gmail.com
 */

package org.rdfhdt.hdt.dictionary.impl;

import org.rdfhdt.hdt.dictionary.DictionarySectionPrivate;
import org.rdfhdt.hdt.dictionary.TempDictionarySection;
import org.rdfhdt.hdt.dictionary.impl.section.HashDictionarySection;
import org.rdfhdt.hdt.dictionary.impl.section.PFCDictionarySection;
import org.rdfhdt.hdt.enums.TripleComponentRole;
import org.rdfhdt.hdt.options.HDTOptions;
import org.rdfhdt.hdt.triples.TempTriples;
import org.rdfhdt.hdt.util.StopWatch;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * @author mario.arias, Eugen
 *
 */
public class MultipleHashDictionary extends MultipleBaseTempDictionary {

	public MultipleHashDictionary(HDTOptions spec) {
		super(spec);
		
		// FIXME: Read types from spec
		subjects = new HashDictionarySection();
		predicates = new HashDictionarySection();
		objects = new HashMap<>();
		shared = new HashDictionarySection();
	}

	private long getNumberObjectsAllSections(){
		Iterator hmIterator = objects.entrySet().iterator();
		// iterate over all subsections in the objects section
		long total = 0;
		while (hmIterator.hasNext()){
			Map.Entry entry = (Map.Entry)hmIterator.next();
			HashDictionarySection subSection = (HashDictionarySection) entry.getValue();
			total += subSection.getNumberOfElements();
		}
		return total;
	}
	/* (non-Javadoc)
	 * @see hdt.dictionary.Dictionary#reorganize(hdt.triples.TempTriples)
	 */
	@Override
	public void reorganize(TempTriples triples) {
		DictionaryIDMapping mapSubj = new DictionaryIDMapping(subjects.getNumberOfElements());
		DictionaryIDMapping mapPred = new DictionaryIDMapping(predicates.getNumberOfElements());
		DictionaryIDMapping mapObj = new DictionaryIDMapping(getNumberObjectsAllSections());
		
		StopWatch st = new StopWatch();
		
		// Generate old subject mapping
		Iterator<? extends CharSequence> itSubj = ((TempDictionarySection) subjects).getEntries();
		while(itSubj.hasNext()) {
			CharSequence str = itSubj.next();
			mapSubj.add(str);
			
			// GENERATE SHARED at the same time
			if(str.length()>0 && str.charAt(0)!='"' && getSubSection(str) != null && getSubSection(str).locate(str)!=0) {
				shared.add(str);
			}
		}
		//System.out.println("Num shared: "+shared.getNumberOfElements()+" in "+st.stopAndShow());

		// Generate old predicate mapping
		st.reset();
		Iterator<? extends CharSequence> itPred = ((TempDictionarySection) predicates).getEntries();
		while(itPred.hasNext()) {
			CharSequence str = itPred.next();
			mapPred.add(str);
		}		
		
		// Generate old object mapping
		Iterator hmIterator = objects.entrySet().iterator();
		// iterate over all subsections in the objects section
		while (hmIterator.hasNext()){
			Map.Entry entry = (Map.Entry) hmIterator.next();
			Iterator<? extends CharSequence> itObj = ((TempDictionarySection) entry.getValue()).getEntries();
			while(itObj.hasNext()) {
				CharSequence str = itObj.next();
				mapObj.add(str);
			}
		}
		// Remove shared from subjects and objects
		Iterator<? extends CharSequence> itShared = ((TempDictionarySection) shared).getEntries();
		while(itShared.hasNext()) {
			CharSequence sharedStr = itShared.next();
			subjects.remove(sharedStr);
			getSubSection(sharedStr).remove(sharedStr);
		}
		//System.out.println("Mapping generated in "+st.stopAndShow());
		
		// Sort sections individually
		st.reset();
		subjects.sort();
		predicates.sort();

		hmIterator = objects.entrySet().iterator();
		// iterate over all subsections in the objects section
		while (hmIterator.hasNext()){
			Map.Entry entry = (Map.Entry) hmIterator.next();
			TempDictionarySection subSection = (TempDictionarySection)entry.getValue();
			subSection.sort();
		}

		shared.sort();
		//System.out.println("Sections sorted in "+ st.stopAndShow());
		
		// Update mappings with new IDs
		st.reset();
		for(long j=0;j<mapSubj.size();j++) {
			mapSubj.setNewID(j, this.stringToId(mapSubj.getString(j), TripleComponentRole.SUBJECT));
//			System.out.print("Subj Old id: "+(j+1) + " New id: "+ mapSubj.getNewID(j)+ " STR: "+mapSubj.getString(j));
		}
		
		for(long j=0;j<mapPred.size();j++) {
			mapPred.setNewID(j, this.stringToId(mapPred.getString(j), TripleComponentRole.PREDICATE));
//			System.out.print("Pred Old id: "+(j+1) + " New id: "+ mapPred.getNewID(j)+ " STR: "+mapPred.getString(j));
		}
		
		for(long j=0;j<mapObj.size();j++) {
			mapObj.setNewID(j, this.stringToId(mapObj.getString(j), TripleComponentRole.OBJECT));
			//System.out.print("Obj Old id: "+(j+1) + " New id: "+ mapObj.getNewID(j)+ " STR: "+mapObj.getString(j));
		}
		//System.out.println("Update mappings in "+st.stopAndShow());
		 
		// Replace old IDs with news
		triples.replaceAllIds(mapSubj, mapPred, mapObj);
		
		//System.out.println("Replace IDs in "+st.stopAndShow());
		
		isOrganized = true;
	}

	@Override
	public TempDictionarySection getObjects() {
		return null;
	}

	@Override
	public void startProcessing() {
		// Do nothing.
	}
	
	@Override
	public void endProcessing() {
		// Do nothing.
	}

	@Override
	public void close() throws IOException {
		// Do nothing.
	}

	private TempDictionarySection getSubSection(CharSequence str){
		if(str.toString().startsWith("\"")){
			String dataType = "";
			if(str.toString().contains("^")){
				dataType = str.toString().split("\\^")[2];
			}else{
				dataType = "NO_DATATYPE";
			}
			return objects.get(dataType);
		}else {
			String key = "";
			if(str.toString().startsWith("_"))
				key = "BLANK";
			else
				key= "URI";
			return objects.get(key);
		}
	}
}

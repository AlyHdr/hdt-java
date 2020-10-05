package org.rdfhdt.hdt.dictionary.impl;

import org.rdfhdt.hdt.dictionary.DictionarySectionPrivate;
import org.rdfhdt.hdt.dictionary.MultTempDictionary;
import org.rdfhdt.hdt.dictionary.TempDictionary;
import org.rdfhdt.hdt.dictionary.TempDictionarySection;
import org.rdfhdt.hdt.dictionary.impl.section.HashDictionarySection;
import org.rdfhdt.hdt.dictionary.impl.section.PFCDictionarySection;
import org.rdfhdt.hdt.enums.DictionarySectionRole;
import org.rdfhdt.hdt.enums.ObjectsSections;
import org.rdfhdt.hdt.enums.TripleComponentRole;
import org.rdfhdt.hdt.exceptions.NotImplementedException;
import org.rdfhdt.hdt.options.HDTOptions;
import org.rdfhdt.hdt.triples.TempTriples;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public abstract class MultipleBaseTempDictionary implements TempDictionary {
    final HDTOptions spec;
    protected boolean isOrganized;

    protected TempDictionarySection subjects;
    protected TempDictionarySection predicates;
    protected HashMap<String,TempDictionarySection> objects;
    protected TempDictionarySection shared;
    public MultipleBaseTempDictionary(HDTOptions spec) {
        this.spec = spec;
    }

    /* (non-Javadoc)
     * @see hdt.dictionary.Dictionary#insert(java.lang.String, datatypes.TripleComponentRole)
     */

    @Override
    public long insert(CharSequence str, TripleComponentRole position) {
        switch(position) {
            case SUBJECT:
                isOrganized = false;
                return subjects.add(str);
            case PREDICATE:
                isOrganized = false;
                return predicates.add(str);
            case OBJECT:
                isOrganized = false;
                getSubSection(str).add(str);
                return getNumberObjectsAllSections();
            default:
                throw new IllegalArgumentException();
        }
    }
    private long getNumberObjectsUntil(CharSequence str){
        long total = 0;
        Iterator iterator = this.objects.entrySet().iterator();
        String type = getObjectType(str);
        while (iterator.hasNext()){
            Map.Entry entry = (Map.Entry)iterator.next();
            HashDictionarySection subSection = (HashDictionarySection) entry.getValue();
            total += subSection.getNumberOfElements();
            if(type.equals((String)entry.getKey()))
                break;
        }
        return total;
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
    private TempDictionarySection getSubSection(CharSequence str){
        if(str.toString().startsWith("\"")){
            String dataType = "";
            if(str.toString().contains("^")){
                dataType = str.toString().split("\\^")[2];
            }else{
                dataType = "NO_DATATYPE";
            }
            if(objects.containsKey(dataType))
                return objects.get(dataType);
            else {
                objects.put(dataType, new HashDictionarySection(this.spec));
                return objects.get(dataType);
            }

        } else {
            String key = "";
            if(str.toString().startsWith("_"))
                key = "BLANK";
            else
                key= "URI";
            if(objects.containsKey(key))
                return objects.get(key);
            else{
                objects.put(key,new HashDictionarySection(this.spec));
                return objects.get(key);
            }
        }
    }
    private String getObjectType(CharSequence str){
        if(str.toString().startsWith("\"")) {
            String dataType = "";
            if(str.toString().contains("^")){
                dataType = str.toString().split("\\^")[2];
            }else{
                dataType = "NO_DATATYPE";
            }
            return dataType;
        }
        else{
            String key = "";
            if(str.toString().startsWith("_"))
                key = "BLANK";
            else
                key= "URI";
            return key;
        }
    }
    @Override
    public void reorganize() {

        // Generate shared
        Iterator<? extends CharSequence> itSubj = ((TempDictionarySection)subjects).getEntries();
        while(itSubj.hasNext()) {
            CharSequence str = itSubj.next();

            // FIXME: These checks really needed?
            // check in the OBJECTS URI section if it can be in shared
            if(str.length()>0 && str.charAt(0)!='"' && objects.get("URI") != null && objects.get("URI").locate(str)!=0) {
                shared.add(str);
            }
        }

        // Remove shared from subjects and objects
        Iterator<? extends CharSequence> itShared = ((TempDictionarySection)shared).getEntries();
        while(itShared.hasNext()) {
            CharSequence sharedStr = itShared.next();
            subjects.remove(sharedStr);
            // removre from the URI section as the shared can't have literals
            objects.get("URI").remove(sharedStr);
        }

        // Sort sections individually
        shared.sort();
        subjects.sort();
        Iterator hmIterator = objects.entrySet().iterator();
        // iterate over all subsections in the objects section
        while (hmIterator.hasNext()){
            Map.Entry entry = (Map.Entry)hmIterator.next();
            TempDictionarySection subSection = (TempDictionarySection)entry.getValue();
            subSection.sort();
        }
        predicates.sort();

        isOrganized = true;

    }
    @Override
    public void reorganize(TempTriples triples) {
        throw new NotImplementedException();
    }

    @Override
    public boolean isOrganized() {
        return isOrganized;
    }

    @Override
    public void clear() {
        subjects.clear();
        predicates.clear();
        shared.clear();
        objects.clear();
    }

    @Override
    public TempDictionarySection getSubjects() {
        return subjects;
    }

    @Override
    public TempDictionarySection getPredicates() {
        return predicates;
    }

    @Override
    public HashMap<String,TempDictionarySection> getAllObjects() {
        return objects;
    }

    @Override
    public TempDictionarySection getShared() {
        return shared;
    }

    // TODO: return the global Id for an object based on the subsection
    protected long getGlobalId(long id, DictionarySectionRole position) {
        switch (position) {
            case SUBJECT:
                return id + shared.getNumberOfElements();
            case OBJECT: {
                Iterator iter = objects.entrySet().iterator();
                int count = 0;
                while (iter.hasNext()){
                    Map.Entry entry = (Map.Entry)iter.next();
                    count+= ((TempDictionarySection)entry.getValue()).getNumberOfElements();
                    if(id<= shared.getNumberOfElements() + count){
                        count -= ((TempDictionarySection)entry.getValue()).getNumberOfElements();
                        break;
                    }

                }
                return shared.getNumberOfElements() + count+id;
            }
            case PREDICATE:
            case SHARED:
                return id;
            default:
                throw new IllegalArgumentException();
        }
    }

    /* (non-Javadoc)
     * @see hdt.dictionary.Dictionary#stringToId(java.lang.CharSequence, datatypes.TripleComponentRole)
     */
    @Override
    public long stringToId(CharSequence str, TripleComponentRole position) {

        if(str==null || str.length()==0) {
            return 0;
        }

        long ret=0;
        switch(position) {
            case SUBJECT:
                ret = shared.locate(str);
                if(ret!=0) {
                    return getGlobalId(ret, DictionarySectionRole.SHARED);
                }
                ret = subjects.locate(str);
                if(ret!=0) {
                    return getGlobalId(ret, DictionarySectionRole.SUBJECT);
                }
                return -1;
            case PREDICATE:
                ret = predicates.locate(str);
                if(ret!=0) {
                    return getGlobalId(ret, DictionarySectionRole.PREDICATE);
                }
                return -1;
            case OBJECT:
                ret = shared.locate(str);
                if(ret!=0) {
                    return getGlobalId(ret, DictionarySectionRole.SHARED);
                }
                long id = getSubSection(str).locate(str);
                String type = getObjectType(str);
                if(id != 0) {
                    Iterator iter = objects.entrySet().iterator();
                    int count = 0;
                    while (iter.hasNext()){
                        Map.Entry entry = (Map.Entry)iter.next();
                        count+= ((TempDictionarySection)entry.getValue()).getNumberOfElements();
                        if(type.equals((String)entry.getKey())){
                            count -= ((TempDictionarySection)entry.getValue()).getNumberOfElements();
                            break;
                        }

                    }
                    return shared.getNumberOfElements() + count+id;
                }
                return -1;
            default:
                throw new IllegalArgumentException();
        }
    }
}

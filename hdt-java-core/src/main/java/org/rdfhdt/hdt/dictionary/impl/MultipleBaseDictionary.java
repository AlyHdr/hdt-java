package org.rdfhdt.hdt.dictionary.impl;

import org.rdfhdt.hdt.dictionary.*;
import org.rdfhdt.hdt.dictionary.impl.section.HashDictionarySection;
import org.rdfhdt.hdt.dictionary.impl.section.PFCDictionarySection;
import org.rdfhdt.hdt.dictionary.impl.section.PFCOptimizedExtractor;
import org.rdfhdt.hdt.enums.DictionarySectionRole;
import org.rdfhdt.hdt.enums.TripleComponentRole;
import org.rdfhdt.hdt.header.Header;
import org.rdfhdt.hdt.listener.ProgressListener;
import org.rdfhdt.hdt.options.ControlInfo;
import org.rdfhdt.hdt.options.HDTOptions;
import org.rdfhdt.hdt.util.io.CountInputStream;
import org.rdfhdt.hdt.util.string.CompactString;
import org.rdfhdt.hdt.util.string.DelayedString;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public abstract class MultipleBaseDictionary implements DictionaryPrivate {

    protected final HDTOptions spec;

    protected DictionarySectionPrivate subjects;
    protected DictionarySectionPrivate predicates;
    protected HashMap<String,DictionarySectionPrivate> objects;
    protected DictionarySectionPrivate shared;

    public MultipleBaseDictionary(HDTOptions spec) {
        this.spec = spec;
    }

    protected long getGlobalId(long id, DictionarySectionRole position,CharSequence str) {
        switch (position) {
            case SUBJECT:
                return id + shared.getNumberOfElements();
            case OBJECT: {
                Iterator iter = objects.entrySet().iterator();
                int count = 0;
                while (iter.hasNext()){
                    Map.Entry entry = (Map.Entry)iter.next();
                    count+= ((DictionarySectionPrivate)entry.getValue()).getNumberOfElements();
                    if(id<= shared.getNumberOfElements() + count){
                        count -= ((DictionarySectionPrivate)entry.getValue()).getNumberOfElements();
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

    /*
    TODO: Change the objects part to look over the sections according to some pointer
     */
    protected long getLocalId(long id, TripleComponentRole position) {
        switch (position) {
            case SUBJECT:
            case OBJECT:
                if(id<=shared.getNumberOfElements()) {
                    return id;
                } else {
                    Iterator hmIterator = objects.entrySet().iterator();
                    // iterate over all subsections in the objects section
                    long count = 0;
                    while (hmIterator.hasNext()){
                        Map.Entry entry = (Map.Entry)hmIterator.next();
                        long numElts = 0;
                        if(entry.getValue() instanceof PFCDictionarySection)
                            numElts = ((PFCDictionarySection)entry.getValue()).getNumberOfElements();
                        else if(entry.getValue() instanceof PFCOptimizedExtractor)
                            numElts = ((PFCOptimizedExtractor)entry.getValue()).getNumStrings();
                        count+= numElts;
                        if(id <= shared.getNumberOfElements()+ count){
                            count -= numElts;
                            break;
                        }
                    }
                    // subtract the number of elements in the shared + the subsections in the objects section
                    return id - count - shared.getNumberOfElements();
                }
            case PREDICATE:
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
        str = DelayedString.unwrap(str);

        if(str==null || str.length()==0) {
            return 0;
        }

        if(str instanceof String) {
            // CompactString is more efficient for the binary search.
            str = new CompactString(str);
        }

        long ret=0;
        switch(position) {
            case SUBJECT:
                ret = shared.locate(str);
                if(ret!=0) {
                    return getGlobalId(ret, DictionarySectionRole.SHARED,str);
                }
                ret = subjects.locate(str);
                if(ret!=0) {
                    return getGlobalId(ret, DictionarySectionRole.SUBJECT,str);
                }
                return -1;
            case PREDICATE:
                ret = predicates.locate(str);
                if(ret!=0) {
                    return getGlobalId(ret, DictionarySectionRole.PREDICATE,str);
                }
                return -1;
            case OBJECT:
                if(str.charAt(0)!='"') {
                    ret = shared.locate(str);
                    if(ret!=0) {
                        return getGlobalId(ret, DictionarySectionRole.SHARED,str);
                    }
                }
                ret = getSubSection(str).locate(str);
                if(ret!=0) {
                    return getGlobalId(ret, DictionarySectionRole.OBJECT,str);
                }
                return -1;
            default:
                throw new IllegalArgumentException();
        }
    }

    private long getNumberObjectsAllSections(){
        Iterator hmIterator = objects.entrySet().iterator();
        // iterate over all subsections in the objects section
        long total = 0;
        while (hmIterator.hasNext()){
            Map.Entry entry = (Map.Entry)hmIterator.next();
            PFCDictionarySection subSection = (PFCDictionarySection) entry.getValue();
            total += subSection.getNumberOfElements();
        }
        return total;
    }
    @Override
    public long getNumberOfElements() {

        return subjects.getNumberOfElements()+predicates.getNumberOfElements()+getNumberObjectsAllSections()+shared.getNumberOfElements();
    }

    @Override
    public long size() {
        return subjects.size()+predicates.size()+objects.size()+shared.size();
    }

    @Override
    public long getNsubjects() {
        return subjects.getNumberOfElements()+shared.getNumberOfElements();
    }

    @Override
    public long getNpredicates() {
        return predicates.getNumberOfElements();
    }

    @Override
    public long getNobjects() {
        return getNumberObjectsAllSections()+shared.getNumberOfElements();
    }

    @Override
    public long getNshared() {
        return shared.getNumberOfElements();
    }

    @Override
    public DictionarySection getSubjects() {
        return subjects;
    }

    @Override
    public DictionarySection getPredicates() {
        return predicates;
    }

    @Override
    public HashMap<String, DictionarySection> getAllObjects() {
        return new HashMap<>(this.objects);
    }

    @Override
    public DictionarySection getObjects() {
        return null;
    }

    @Override
    public DictionarySection getShared() {
        return shared;
    }

    private DictionarySectionPrivate getSection(long id, TripleComponentRole role) {
        switch (role) {
            case SUBJECT:
                if(id<=shared.getNumberOfElements()) {
                    return shared;
                } else {
                    return subjects;
                }
            case PREDICATE:
                return predicates;
            case OBJECT:
                if(id<=shared.getNumberOfElements()) {
                    return shared;
                } else {

                    Iterator hmIterator = objects.entrySet().iterator();
                    // iterate over all subsections in the objects section
                    DictionarySectionPrivate desiredSection = null;
                    int count = 0;
                    while (hmIterator.hasNext()){
                        Map.Entry entry = (Map.Entry)hmIterator.next();
                        DictionarySectionPrivate subSection = (DictionarySectionPrivate)entry.getValue();
                        count += subSection.getNumberOfElements();
                        if(id <= shared.getNumberOfElements()+ count){
                            desiredSection = subSection;
                            break;
                        }
                    }
                    return desiredSection;
                }
            default:
                throw new IllegalArgumentException();
        }
    }

    /* (non-Javadoc)
     * @see hdt.dictionary.Dictionary#idToString(int, datatypes.TripleComponentRole)
     */
    @Override
    public CharSequence idToString(long id, TripleComponentRole role) {
        DictionarySectionPrivate section = getSection(id, role);
        long localId = getLocalId(id, role);
        return section.extract(localId);
    }
    private DictionarySectionPrivate getSubSection(CharSequence str){
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
                objects.put(dataType, new PFCDictionarySection(this.spec));
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
                objects.put(key,new PFCDictionarySection(this.spec));
                return objects.get(key);
            }
        }
    }
}

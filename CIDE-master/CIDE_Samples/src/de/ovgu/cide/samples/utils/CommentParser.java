package de.ovgu.cide.samples.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;


public class CommentParser {
	public static final String OPENTAG_BEGIN = "[";
	public static final String OPENTAG_END = "[/";
	public static final String CLOSETAG = "]";
	
	public static final String DESCRIPTION_TAG = "description";
	public static final String REQUIREMENT_CATEGORYS_TAG = "requirementcategory";
	public static final String REQUIREMENT_CATEGORY_NAME_TAG = "categoryname";
	public static final String REQUIREMENT_TAG = "requirement";
	public static final String PLUGIN_ID_TAG = "pluginid";
	public static final String PLUGIN_MISSING_MSG_TAG = "errormsg";
	
	
	private String comment;
	private String desc;
	private List<RequirementCategory> req = new ArrayList<RequirementCategory>();
	
	
	public CommentParser(String comment){
		this.comment = comment.replaceAll("\n", "").replaceAll("\t", "");
		
	}
	
	private String getTextInTag(String tag, String text, int fromIdx) {
		String openTag;
		int startId = text.indexOf(openTag = (OPENTAG_BEGIN + tag +CLOSETAG), fromIdx);
		int endId = text.indexOf(OPENTAG_END + tag +CLOSETAG, startId + openTag.length());
		
		if (startId < 0 || endId < 0 )
			return "";
		
		return text.substring(startId+openTag.length(), endId);	
			
	}
	
	private List<String> getElements(String tag, String text, int fromIdx) {
		String openTag;
		List<String> results = new ArrayList<String>();
		
		
		int startId = 0; 
		int endId = 0;
		
		startId = text.indexOf(openTag = (OPENTAG_BEGIN + tag +CLOSETAG), fromIdx);
		endId = text.indexOf(OPENTAG_END + tag +CLOSETAG, startId + openTag.length());
		
		while (startId >= 0 && endId >= 0 && startId < endId)  {
			results.add(text.substring(startId+openTag.length(), endId));
			
			fromIdx = endId + 1;
			
			startId = text.indexOf(openTag = (OPENTAG_BEGIN + tag +CLOSETAG), fromIdx);
			endId = text.indexOf(OPENTAG_END + tag +CLOSETAG, startId + openTag.length());
				
		}
		
		return results;

			
	}
	
	private String getTextInTag(String tag, String text) {

		return getTextInTag(tag,text,0);	
		
	}
	
	public String getDescription() {
	
		if (desc != null)
			return desc;
		
		return desc = getTextInTag(DESCRIPTION_TAG, comment);		
		
	}
	
	public List<RequirementCategory> getRequirements() {
		
		if (req.size() > 0)
			return req;
		
		//get the requirements
		List<String> reqCats = getElements(REQUIREMENT_CATEGORYS_TAG, comment, 0);
		Iterator<String> i = reqCats.iterator();
		
		String reqGroup;
		String catName;
		String curReq;
		Map<String, String> requirements;
		
		while (i.hasNext()) {
			reqGroup = (String) i.next();
			catName = getTextInTag(REQUIREMENT_CATEGORY_NAME_TAG, reqGroup);
			requirements = new HashMap<String, String> ();
			List<String> reqs = getElements(REQUIREMENT_TAG, reqGroup, 0);
			Iterator<String> j = reqs.iterator();
			
			while(j.hasNext()) {
				curReq = (String) j.next();
				requirements.put(getTextInTag(PLUGIN_ID_TAG, curReq),getTextInTag(PLUGIN_MISSING_MSG_TAG, curReq));
				
			}
			//create a requirementcategory and add it to the results
			req.add(new RequirementCategory(catName,requirements));			
			
		}
		
		return req;		
		
	}
	

}

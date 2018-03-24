package com.fun.zpetchain;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fun.zpetchain.model.Pet;
import com.fun.zpetchain.util.HttpUtil;
import com.fun.zpetchain.util.OcrUtil;
import com.fun.zpetchain.util.PropUtil;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

/**
 * let's go tools for study
 * automatically refresh the dog market and purchase
 * <br><b>Copyright 2018 the original author or authors.</b>
 * @author 2bears
 * @since
 * @version 1.0
 */
public class PetChain {
	
	private static final Logger logger = LoggerFactory.getLogger(PetChain.class);
	
	/**
	 * urls of interfaces
	 */
	private final static String MARKET_URL = "https://pet-chain.baidu.com/data/market/queryPetsOnSale";
	private final static String CAPTCHA_URL = "https://pet-chain.baidu.com/data/captcha/gen";
	private final static String PURCHASE_URL = "https://pet-chain.baidu.com/data/txn/create";
	
	/**
	 * rare degree of dogs, common to legend
	 */
	private final static int LEGEND = 5;
	private final static int MYTH = 4;
	private final static int EPIC = 3;
	private final static int EXCELLENCE = 2;
	private final static int RARE = 1;
	private final static int COMMON = 0;
	
	/**
	 * key=rare degree, value=price limit
	 * when price <= price limit, try purchase
	 */
	private final static Map<Integer, Integer> LIMIT_MAP = new HashMap<Integer, Integer>();
	
	/**
	 * when price <= LOWER_AMT, no matter other conditions, try to purchase
	 * if don't use this condition, set it to 0
	 */
	private static int LOWER_AMT = 0;
	
	/**
	 * when rage degree <= rare, purchase should meet the below condition:
	 * (price of the sixth - price of fist) >= DIF_AMT
	 * if don't use this condition, set it to 0
	 */
	private static int DIF_AMT = 0;
	
	private final static String SORT_TYPE_AMT = "AMOUNT_ASC";
	private final static String SORT_TYPE_TIME = "CREATETIME_DESC";
	
	/**
	 * 筛选条件: 卓越 休息0分钟 状态 正常
	 */
	private final static String FILTER_COND_EXC = "{\"1\":\"2\",\"3\":\"0-1\",\"6\":\"1\"}";
	/**
	 * 筛选条件: epic 休息0分钟 状态 正常
	 */
	private final static String FILTER_COND_EPIC = "{\"1\":\"3\"}";
	/**
	 * 筛选条件: myth 休息0分钟 状态 正常
	 */
	private final static String FILTER_COND_MYTH = "{\"1\":\"4\"}";
			
	/**
	 * when captcha is wrong, try more times
	 */
	private final static int RETRY_TIMES = 10;
	
	
	public static void main(String []args)  {
		
		PetChain petChain = new PetChain();
		try {
			petChain.initProp();
		} catch (Exception e) {
			logger.error("load properties fail, stop...");
		}
		
		while(true) {
			try {
				petChain.queryMarket(SORT_TYPE_AMT, FILTER_COND_MYTH);
				Thread.sleep(200);
				petChain.queryMarket(SORT_TYPE_AMT, FILTER_COND_EPIC);
		//		petChain.queryMarket(SORT_TYPE_AMT, FILTER_COND_MYTH);
				Thread.sleep(1000);
////				Thread.sleep(200);
//				petChain.queryMarket(SORT_TYPE_TIME);
//				
			} catch (Exception e) {
				logger.warn("exception:" + e.getMessage());
			}
		}
	}
	
	private void initProp() throws Exception {
		LIMIT_MAP.put(COMMON, Integer.parseInt(PropUtil.getProp("price_common")));  
		LIMIT_MAP.put(RARE, Integer.parseInt(PropUtil.getProp("price_rare")));               
		LIMIT_MAP.put(EXCELLENCE, Integer.parseInt(PropUtil.getProp("price_excellence")));        
		LIMIT_MAP.put(EPIC, Integer.parseInt(PropUtil.getProp("price_epic")));              
		LIMIT_MAP.put(MYTH, Integer.parseInt(PropUtil.getProp("price_myth")));              
		LIMIT_MAP.put(LEGEND, Integer.parseInt(PropUtil.getProp("price_legend"))); 	
		
		LOWER_AMT = Integer.parseInt(PropUtil.getProp("lower_amt"));
		DIF_AMT = Integer.parseInt(PropUtil.getProp("dif_amt"));
	}
	
	/**
	 * query the market, if buy conditions meet, try to purchase
	 * @author 2bears
	 * @since
	 * @param sortType
	 */
	private void queryMarket(String sortType, String filterCondition) {	
	    Map<String, Object> paraMap = new HashMap<String, Object>(16);
	    paraMap.put("appId", 1);
	    paraMap.put("lastAmount", "");
	    paraMap.put("lastRareDegree", "");
	    paraMap.put("pageNo", 1);
	    paraMap.put("pageSize", 10);
	    paraMap.put("querySortType", sortType);
	    paraMap.put("requestId", System.currentTimeMillis());
	    paraMap.put("tpl", "");
	    paraMap.put("petIds", new int[]{});
	    paraMap.put("filterCondition", filterCondition);
	    paraMap.put("nounce", null);
	    paraMap.put("type", null);                  //
	    paraMap.put("token", null);
	    paraMap.put("timeStamp", null);
	    
	    String data = JSONObject.fromObject(paraMap).toString();
   
	    JSONObject jsonResult = HttpUtil.doJsonPost(MARKET_URL, data, 1000, 1000);   
    
	    if(jsonResult != null && "success".equals(jsonResult.get("errorMsg"))) {
	    	Pet []petArr = (Pet [])JSONArray.toArray(jsonResult.getJSONObject("data").getJSONArray("petsOnSale"), Pet.class);
	    	int retry = 0;
	    	Pet pet = choosePetFromPetArr(petArr, sortType);
	    	if(pet != null) {
	    		logger.info("best amt:{}, degree:{}, petId:{}", pet.getAmount(), pet.getRareDegree(), pet.getPetId());
	    		logger.info("try to putchase...");
	    		while(retry <= RETRY_TIMES) {
		    		retry++;
	    			if(purchase(pet)) {
	    				break;
	    			} else {
	    				try {
							Thread.sleep(100);
						} catch (InterruptedException e) {
						}
	    				continue;
	    			}
	    		}
	    	}
	    }     
	}
	
	/**
	 * find out the pet that meets the buying conditions
	 * @author 2bears
	 * @since
	 * @param petArr
	 * @param sortType
	 * @return
	 */
	private Pet choosePetFromPetArr(Pet []petArr, String sortType) {

    	Map<Integer, Pet> lowestPetMap = new HashMap<Integer, Pet>(16);
    	Pet pet5 = petArr[4];
		for(Pet pet : petArr) {
			// 只考虑0代
			if(pet.getGeneration() >= 0) {
				Pet itemPet = lowestPetMap.get(pet.getRareDegree());
				if(itemPet == null) {
					lowestPetMap.put(pet.getRareDegree(), pet);
				} else {
					if(pet.getAmount() < itemPet.getAmount()) {
						lowestPetMap.put(pet.getRareDegree(), itemPet);
					}
				}
			}
		}
		
		System.out.println("");
		for(int degree = COMMON; degree <= LEGEND; degree++) {
			Pet petPrt = lowestPetMap.get(degree);
			if(petPrt != null) {
		    	System.out.println(String.format("%s: amt:%s, degree:%s, petId:%s", sortType, petPrt.getAmount(), petPrt.getRareDegree(), petPrt.getPetId()));				
			}
		}
		
		Pet pet = null;
    	int []rareDegree = new int[] {LEGEND, MYTH, EPIC, EXCELLENCE};
    	for(int degree : rareDegree) {
    		pet = lowestPetMap.get(degree);
    		if(pet != null && pet.getAmount() <= LIMIT_MAP.get(pet.getRareDegree())) {
    			return pet;
    		}
    	}
    	
    	int []commonDegree = new int[] {RARE, COMMON};
    	for(int degree : commonDegree) {
    		pet = lowestPetMap.get(degree);
    		if(pet != null && pet.getAmount() < LOWER_AMT) {
    			return pet;
    		}
    		if(pet != null && pet5 != null && SORT_TYPE_AMT.equals(sortType)) {
    			if(pet5.getAmount() - pet.getAmount() >= DIF_AMT && pet.getAmount() <= LIMIT_MAP.get(pet.getRareDegree())) {
    				return pet;
    			}
    		}
    	}
   	
		return null;
	}
	
	/**
	 * try to purchase, if success or others have purchased, don't purchase again
	 * @author 2bears
	 * @since
	 * @param pet
	 * @return true-can try again false-don't try again
	 */
	private boolean purchase(Pet pet) {
		Map<String, String> vCodeMap = getCaptcha();
		if(vCodeMap == null) {
			return false;
		}
	    Map<String, Object> paraMap = new HashMap<String, Object>(16);
	    paraMap.put("appId", 1);
	    paraMap.put("tpl", "");
	    paraMap.put("requestId", System.currentTimeMillis());
	    paraMap.put("seed", vCodeMap.get("seed"));
	    paraMap.put("captcha", vCodeMap.get("vCode"));
	    paraMap.put("petId", pet.getPetId());
	    paraMap.put("validCode", pet.getValidCode());
	    paraMap.put("amount", pet.getAmount());
	    String data = JSONObject.fromObject(paraMap).toString();
	    
	    try { 
		    JSONObject jsonResult = HttpUtil.doJsonPost(PURCHASE_URL, data, 1000, 1000);
			
		    if(jsonResult != null) {
		    	logger.info(jsonResult.toString());
		    }
		    
		    if(jsonResult != null) {
		    	String errorMsg = jsonResult.getString("errorMsg");
		    	if("success".equals(errorMsg) || "有人抢先下单啦".equals(errorMsg)) {
		    		return true;	
		    	} else {
		    		return false;
		    	}
		    	
		    } else {
		    	return false;
		    }
	    } catch(Exception e) {
	    	System.out.println("purchase error:" + e.getMessage());
	    	return false;
	    }
	}
	
	/**
	 * get the captcha from interface, and then identify the captcha by OCR
	 * @author 2bears
	 * @since
	 * @return Map: captcha seed and captcha code
	 */
	private Map<String, String> getCaptcha() {
		
	    Map<String, Object> paraMap = new HashMap<String, Object>(8);
	    paraMap.put("appId", 1);
	    paraMap.put("requestId", String.valueOf(System.currentTimeMillis()));
	    paraMap.put("tpl", "");
	    paraMap.put("nounce", null);
	    paraMap.put("timeStamp", null);
	    paraMap.put("token", null);
	    
		JSONObject jsonResult = HttpUtil.doJsonPost(CAPTCHA_URL, JSONObject.fromObject(paraMap).toString(), 1000, 1000);

	    try {
	    	if(jsonResult == null) {
	    		return null;
	    	}
		    String imgData = jsonResult.getJSONObject("data").get("img").toString();
		    String seed = jsonResult.getJSONObject("data").get("seed").toString();
			InputStream is = new ByteArrayInputStream(Base64.getDecoder().decode(imgData));
			BufferedImage image = ImageIO.read(is);

			String vCode = OcrUtil.ocrByTesseract(image);
			
			if(StringUtils.isNotEmpty(vCode) && vCode.length() > 4) {
				vCode = vCode.substring(vCode.length() - 4);
			}
			if(StringUtils.isNotEmpty(vCode) && vCode.length() == 3) {
				vCode = "G" + vCode;
			}			
			
			if(StringUtils.isNotEmpty(vCode) && vCode.length() == 4) {
				Map<String, String> vCodeMap = new HashMap<String, String>(4);
				vCodeMap.put("seed", seed);
				vCodeMap.put("vCode", vCode);
				return vCodeMap;
			} else {
				logger.info("ocr captcha error [{}]", vCode);
			}
			is.close();
		} catch (Exception e) {
			logger.error(e.getMessage());
		} finally {	
		}     
	    return null;		
	}

}

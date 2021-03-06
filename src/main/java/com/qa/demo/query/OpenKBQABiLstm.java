package com.qa.demo.query;

import com.qa.demo.algorithm.EditingDistance;
import com.qa.demo.conf.Configuration;
import com.qa.demo.dataStructure.*;
import com.qa.demo.questionAnalysis.QuestionAnalysisDriver;
import com.qa.demo.questionAnalysis.QuestionAnalysisDriverImplBiLSTM;
import com.qa.demo.questionAnalysis.Segmentation;
import com.qa.demo.utils.w2v.Result;
import com.qa.demo.utils.w2v.Word2VecGensimModel;
import org.elasticsearch.common.collect.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.*;

import static com.qa.demo.conf.Configuration.*;
import static com.qa.demo.utils.w2v.Subword2Vec.calVec;
import static com.qa.demo.utils.w2v.Subword2Vec.check_words;

/**
 * @author J.Y.Zhang
 * @create 2018-04-14
 * Function description:
 *  获取以问题中实体为主语或宾语的所有相关三元组
 *  并计算三元组中的谓词与问题中其他词的三种相似度
 *  结果分别存储在Question结构中的tripletList和questionTokenTripletSim中
 **/
public class OpenKBQABiLstm implements KbqaQueryDriver {
    private static final Logger infologger = LoggerFactory.getLogger("queryLoggerInfo");
    private static final Logger logger = LoggerFactory.getLogger(OpenKBQA.class);

    @Override
    public Question kbQueryAnswers(Question q) {

        //取得问题分析器驱动；
        QuestionAnalysisDriver qAnalysisDriver = new QuestionAnalysisDriverImplBiLSTM();
        //logger.info("entity");
        //q = qAnalysisDriver.nerQuestion(q);
        //logger.info("token");
        //((QuestionAnalysisDriverImplBiLSTM) qAnalysisDriver).generateEntityFIle();

        //logger.info("entityList.txt is generated!!!!");
        //q = qAnalysisDriver.segmentationQuestion(q);

        // 得到所有以问题中实体为主语或宾语的所有相关三元组
        entityReturnedResults resultVo = SendPOSTRequest.getEntityFromFlaskServer(q.getQuestionString());
        List<String> entitystr = resultVo.getEntity();
        List<String> url = resultVo.getUrl();
        List<String> remain = resultVo.getRemain();

        logger.info("ner:entity:");
        for(String item:entitystr)
            logger.info(item);


        logger.info("ner:url:");
        for(String item:url)
            logger.info(item);

        logger.info("ner:remain:");
        for(String item:remain)
            logger.info(item);
        ArrayList<Entity> entityList = new ArrayList<>();
        for(int i=0;i<entitystr.size();i++)
        {
            String remainQuestion = remain.get(i);
            //q.setQuestionString(remainQuestion);
            String url_item = url.get(i);
            String entity_item = entitystr.get(i);

            if(url_item.equals("None"))
            {



                Entity e1 = new Entity();
                String entityURL1 = ENTITY_PREFIX_BAIDU + entity_item;
                e1.setEntityURI(entityURL1);
                e1.setKgEntityName(entity_item);
                entityList.add(e1);





            }
            else
            {
                Entity e = new Entity();

                e.setEntityURI(url_item);
                e.setKgEntityName(entity_item);

                entityList.add(e);

            }




        }

        q.setQuestionEntity(entityList);
        Map<String,String> candidate = new HashMap();
        List<Predicate> ps = new ArrayList<Predicate>();
        List<Double> scores = new ArrayList<Double>();
        logger.info("triplets");
        for(int i=0;i<entitystr.size();i++)
        {
            Entity temp_entity=q.getQuestionEntity().get(i);
            List<Triplet> temp  = GetCandidateAnswers.getCandidateTripletsByEntity(temp_entity);
            Iterator<Triplet> iter = temp.iterator();
            while(iter.hasNext()){
                Triplet triplet = iter.next();
                String PredicateName = triplet.getPredicateURI().substring(triplet.getPredicateURI().lastIndexOf("/")+1);
                //System.out.println("new----");
                //System.out.println(PredicateName);
                if(PredicateName.equals("relatedPage") || PredicateName.equals("internalLink") || PredicateName.equals("relatedImage")
                        || PredicateName.equals("externalLink") || PredicateName.equals("category")|| PredicateName.equals("abstract") || PredicateName.equals("pageRedirects") || PredicateName.equals("中文名：") || PredicateName.equals("中文名")|| PredicateName.equals("pageDisambiguates")){
                    iter.remove();
                }
            }

            StringBuffer cadidatePredicateList = new StringBuffer();
            for(Triplet t: temp){ // 问题中所有实体对应作为主语或宾语的三元组 去重
                String pt = t.getPredicateURI().substring(t.getPredicateURI().lastIndexOf("/")+1);
                String ot = t.getObjectURI().substring(t.getObjectURI().lastIndexOf("/")+1);
                cadidatePredicateList.append(pt);
                cadidatePredicateList.append(" ");
                candidate.put(pt, ot);
                System.out.println(pt);
            }
            String cadidate = cadidatePredicateList.toString();
            System.out.println("predicate");
            predicateReturnedResults presultVo = SendPOSTRequest.getPredicateFromFlaskServer(remain.get(i),cadidate);
            String predicate = presultVo.getPredicate();

            Double score = presultVo.getScore();
            System.out.println(predicate);
            Predicate p = new Predicate();
            p.setKgPredicateName(predicate);
            System.out.println("score");
            scores.add(score);
            System.out.println(score);
            ps.add(p);

        }
        //q = getCandidateTripletsWithoutURL(q);
        //Map<String,String> candidate = new HashMap();
        //ArrayList<String> cadidatePredicateList = new ArrayList<>();


        q.setQuestionPredicate(ps);
        logger.info("triplets");
        //ArrayList<QueryTuple> tuples = genenrateQueryTuple(q,predicate);
        //q.setQueryTuples(tuples);
        //q = GetCandidateAnswers.getCandidateAnswers(q, DataSource.BiLSTM);
        //ArrayList<Answer> answers = _getWebServiceCandidateAnswers(q, p);

        int index_max=0;
        for (int i=0;i<scores.size();i++)
        {
            if(scores.get(i)>scores.get(index_max))
                index_max=i;
        }


        ArrayList<Answer> results = new ArrayList<>();

        Answer a = new Answer();
        String answerStr = candidate.get(ps.get(index_max).getKgPredicateName());
        System.out.println("answer:");
        System.out.println(answerStr);
        a.setAnswerString(answerStr);
        //如果没有候选答案则返回一个默认答案同时将分数置为0；

        results.add(a);
        q.setCandidateAnswer(results);
        q.setReturnedAnswer(a);
        return q;
    }
    private Question getCandidateTriplets(Question q){

        Set<Triplet> set = new HashSet<>();
        for(Entity entity: q.getQuestionEntity()){ // 问题中所有实体对应作为主语或宾语的三元组 去重
            List<Triplet> temp  = GetCandidateAnswers.getCandidateTripletsByEntity(entity);
            set.addAll(temp);
        }

        List<Triplet> tripletList = new ArrayList<>(set);

        Iterator<Triplet> iter = tripletList.iterator();
        while(iter.hasNext()){
            Triplet triplet = iter.next();
            String PredicateName = triplet.getPredicateURI().substring(triplet.getPredicateURI().lastIndexOf("/")+1);
            if(PredicateName.equals("relatedPage") || PredicateName.equals("internalLink") || PredicateName.equals("relatedImage")
                    || PredicateName.equals("externalLink") || PredicateName.equals("category") ){
                iter.remove();
            }
        }

        q.setTripletList(tripletList);

        return q;
    }
    private Question getCandidateTripletsWithoutURL(Question q){

        Set<Triplet> set = new HashSet<>();
        for(Entity entity: q.getQuestionEntity()){ // 问题中所有实体对应作为主语或宾语的三元组 去重
            List<Triplet> temp  = GetCandidateAnswers.getCandidateTripletsByEntityWithoutURL(entity);
            set.addAll(temp);
        }

        List<Triplet> tripletList = new ArrayList<>(set);

        Iterator<Triplet> iter = tripletList.iterator();
        while(iter.hasNext()){
            Triplet triplet = iter.next();
            String PredicateName = triplet.getPredicateURI().substring(triplet.getPredicateURI().lastIndexOf("/")+1);
            String ObjectName = triplet.getObjectURI().substring(triplet.getObjectURI().lastIndexOf("/")+1);
            if(PredicateName.equals("relatedPage") || PredicateName.equals("internalLink") || PredicateName.equals("relatedImage")
                    || PredicateName.equals("externalLink") || PredicateName.equals("pageDisambiguates")||PredicateName.equals("pageRedirects") || PredicateName.equals("category") ){
                iter.remove();
            }

            else if(PredicateName.equals("abstract"))
            {
                triplet.setPredicateName("简介");
                triplet.setObjectName(ObjectName);
            }
            else
            {
                triplet.setPredicateName(PredicateName);
                triplet.setObjectName(ObjectName);
            }

        }

        q.setTripletList(tripletList);

        return q;
    }

    // 计算相似度 根据词向量和距离
    private Question calSimilarity(Question q){
        HashMap<Entity,ArrayList<String>> questionToken = q.getQuestionToken();
        HashMap<Triplet,HashMap<String,List<Double>>> questionTokenTripletSim = new HashMap<>();
        ArrayList<Answer> answers = new ArrayList<>();
        if (q.getTripletList() == null || q.getTripletList().isEmpty()){
            q.setCandidateAnswer(answers);
            return q;
        }
        HashMap<List<String>,List<Double>> map = new HashMap<>();
        for(Triplet triplet: q.getTripletList()){ // 这个循环的是所有三元组
            if(triplet.getObjectURI()==null || triplet.getPredicateURI()==null) continue;
            if(triplet.getObjectURI().split("/").length < 1 || triplet.getPredicateURI().split("/").length < 1) continue;
            String predicateName = triplet.getPredicateURI().split("/")[triplet.getPredicateURI().split("/").length-1];

            String objectName = triplet.getObjectURI().split("/")[triplet.getObjectURI().split("/").length-1];
            for (String punctuation : Configuration.PUNCTUATION_SET) { // 去掉特殊符号
                predicateName = predicateName.replace(punctuation, "");
                //objectName = objectName.replace(punctuation, "");
            }
            predicateName = predicateName.replace("：", "");
            //objectName = objectName.replace("：", "");
            if(predicateName == "" || objectName == "") continue;

            Iterator<Map.Entry<Entity,ArrayList<String>>> it = questionToken.entrySet().iterator();
            HashMap<String,List<Double>> preSim = new HashMap<>();
            Answer ans = new Answer();
            double score = 0;
            while (it.hasNext()) { // 这个循环的是问题里所有非实体的词
                Map.Entry<Entity,ArrayList<String>> entry = it.next();

                for (String entryName:  entry.getValue()){

                    if(Configuration.PUNCTUATION_SET.contains(entryName)) continue;
                    if(entryName.equals("《") || entryName.equals("》")) continue;

                    List<String> ss = Arrays.asList(predicateName,entryName);
                    if(!map.containsKey(ss)){// 避免重复计算
                        // 计算相似度 **新疆小麦的分布和颜色？** 新疆小麦对应三元组谓词有：中文名，产地等 这里计算的是中文名和分布的相似度
                        // 谓词有很多四字/三字词语 直接算相似度 都是0 怎么做 拆开试一下。。。
                        int sub_size = 2;
                        double word_sim = this.calTwoWordsSimilarityMethods(predicateName,entryName,"word");
                        if(predicateName.length() > 2 && word_sim == 0){
                            Segmentation LongNameSplit = new Segmentation();
                            LongNameSplit.segmentation(predicateName);

                            List<String> tokens =  LongNameSplit.getTokens();
                            int size =  tokens.size();

                            for(String token: tokens){
                                word_sim += this.calTwoWordsSimilarityMethods(token,entryName,"word");
                                //subword_sim += this.calTwoWordsSimilarityMethods(token,entryName,"distance");
                            }
                            word_sim /= size;
                        }
                        double char_sim = 0;//
                        //double char_sim = this.calTwoWordsSimilarityMethods(predicateName,entryName,"character");
                        double dis_sim = this.calTwoWordsSimilarityMethods(predicateName,entryName,"distance");
                        double total_sim = word_sim + char_sim + dis_sim; // 不同的相似度区间不一样  直接加0.0待优化啊
                        infologger.info("三元组里的谓词:" + predicateName + " 问题里非实体词:" + entryName
                                + " 词相似:" + word_sim+ " 字相似:" + char_sim+ " 距离相似:" + dis_sim);
                        List<Double> list = Arrays.asList(word_sim, char_sim, dis_sim, total_sim);
                        score += total_sim;
                        preSim.put(entryName,list);

                        map.put(ss,list);
                    }else{
                        score += map.get(ss).get(3);
                        preSim.put(entryName,map.get(ss));
                    }
                }
            }
            ans.setAnswerTriplet(Collections.singletonList(triplet));
            ans.setAnswerScore(score);
            ans.setAnswerString(objectName);
            ans.setAnswerSource("net");
            answers.add(ans);
            questionTokenTripletSim.put(triplet,preSim);
        }
        q.setCandidateAnswer(answers);
        q.setQuestionTokenTripletSim(questionTokenTripletSim);
        return q;
    }

    // 计算相似度 根据词向量和距离 考虑词频信息  目前 参数a = 0.1 提升3%左右
    private Question calSimilarityWithWeight(Question q){
        // 求和之前考虑单词出现的频率做权重 参考A SIMPLE BUT TOUGH-TO-BEAT BASELINE FOR SENTENCE EMBEDDINGS
        // 统计词频 统计所有候选三元组的谓词
        Tuple<Integer,Map<String, Double>> word_t = wordFrequency(q);
        Map<String, Double> wordFre = word_t.v2();
        int total = word_t.v1();
        double arg = 0.1;

        HashMap<Entity,ArrayList<String>> questionToken = q.getQuestionToken();
        HashMap<Triplet,HashMap<String,List<Double>>> questionTokenTripletSim = new HashMap<>();
        ArrayList<Answer> answers = new ArrayList<>();
        if (q.getTripletList() == null || q.getTripletList().isEmpty()){
            q.setCandidateAnswer(answers);
            return q;
        }
        HashMap<List<String>,List<Double>> map = new HashMap<>();
        for(Triplet triplet: q.getTripletList()){ // 这个循环的是所有三元组
            if(triplet.getObjectURI()==null || triplet.getPredicateURI()==null) continue;
            if(triplet.getObjectURI().split("/").length < 1 || triplet.getPredicateURI().split("/").length < 1) continue;
            String predicateName = triplet.getPredicateURI().split("/")[triplet.getPredicateURI().split("/").length-1];

            String objectName = triplet.getObjectURI().split("/")[triplet.getObjectURI().split("/").length-1];
            for (String punctuation : Configuration.PUNCTUATION_SET) { // 去掉特殊符号
                predicateName = predicateName.replace(punctuation, "");
                //objectName = objectName.replace(punctuation, "");
            }
            predicateName = predicateName.replace("：", "");
            //objectName = objectName.replace("：", "");
            if(predicateName == "" || objectName == "") continue;

            Iterator<Map.Entry<Entity,ArrayList<String>>> it = questionToken.entrySet().iterator();
            HashMap<String,List<Double>> preSim = new HashMap<>();
            Answer ans = new Answer();
            double score = 0;
            while (it.hasNext()) { // 这个循环的是问题里所有非实体的词
                Map.Entry<Entity,ArrayList<String>> entry = it.next();

                for (String entryName:  entry.getValue()){

                    if(Configuration.PUNCTUATION_SET.contains(entryName)) continue;
                    if(entryName.equals("《") || entryName.equals("》")) continue;

                    List<String> ss = Arrays.asList(predicateName,entryName);
                    if(!map.containsKey(ss)){// 避免重复计算
                        // 计算相似度 **新疆小麦的分布和颜色？** 新疆小麦对应三元组谓词有：中文名，产地等 这里计算的是中文名和分布的相似度
                        // 谓词有很多四字/三字词语 直接算相似度 都是0 怎么做 拆开试一下。。。
                        int sub_size = 2;
                        double wfre = wordFre.get(predicateName);
                        double wa = arg;
                        double word_sim =  (wa/(wa+wfre)) * this.calTwoWordsSimilarityMethods(predicateName,entryName,"word");
                        if(predicateName.length() > 2 && word_sim == 0){
                            infologger.info(">2=0");
                            Segmentation LongNameSplit = new Segmentation();
                            LongNameSplit.segmentation(predicateName);

                            List<String> tokens =  LongNameSplit.getTokens();
                            int size =  tokens.size();

                            for(String token: tokens){
                                double fre = wordFre.get(token);
                                double a = arg;
                                word_sim += (a/(a+fre)) * this.calTwoWordsSimilarityMethods(token,entryName,"word");
                            }
                            word_sim /= size;
                        }
                        double char_sim = 0;
                        double dis_sim = (wa/(wa+wfre)) * this.calTwoWordsSimilarityMethods(predicateName,entryName,"distance");
                        double total_sim = word_sim + dis_sim; // 不同的相似度区间不一样  直接加0.0待优化啊
                        infologger.info("三元组里的谓词:" + predicateName + " 问题里非实体词:" + entryName
                                + " 词相似:" + word_sim + " 距离相似：" + dis_sim);
                        List<Double> list = Arrays.asList(word_sim, char_sim, dis_sim, total_sim);
                        score += total_sim;
                        preSim.put(entryName,list);

                        map.put(ss,list);
                    }else{
                        score += map.get(ss).get(3);
                        preSim.put(entryName,map.get(ss));
                    }
                }
            }
            ans.setAnswerTriplet(Collections.singletonList(triplet));
            ans.setAnswerScore(score);
            ans.setAnswerString(objectName);
            ans.setAnswerSource("net");
            answers.add(ans);
            questionTokenTripletSim.put(triplet,preSim);
        }
        q.setCandidateAnswer(answers);
        q.setQuestionTokenTripletSim(questionTokenTripletSim);
        return q;
    }

    // 统计词频 返回tuple<token总数，map<token,频率> >
    private Tuple<Integer,Map<String, Double>> wordFrequency(Question q){
        // 统计三元组的词频率
        HashMap<String, Integer> token_map = new HashMap<>(); // 存（token，token在所有三元组中出现的次数）
        HashMap<String, Double> fre_map = new HashMap<>();   // 存（token，token次数/token总数）
        int total = 0;
        for(Triplet triplet: q.getTripletList()) { // 这个循环的是所有三元组
            String predicateName = triplet.getPredicateURI().split("/")[triplet.getPredicateURI().split("/").length-1];

            for (String punctuation : Configuration.PUNCTUATION_SET) { // 去掉特殊符号
                predicateName = predicateName.replace(punctuation, "");
            }
            predicateName = predicateName.replace("：", "");
            if(predicateName == "") continue;
            token_map.put(predicateName,1);
            total += 1;
            Segmentation LongNameSplit = new Segmentation();
            LongNameSplit.segmentation(predicateName);
            List<String> tokens =  LongNameSplit.getTokens();
            for(String token: tokens){
                total += 1;
                if(token_map.containsKey(token)){
                    token_map.put(token, token_map.get(token)+1);
                }else{
                    token_map.put(token, 1);
                }
            }
        }
        infologger.info("total"+total);
        for(String key:token_map.keySet()){
            fre_map.put(key, ((double)token_map.get(key)/(double)total));
        }
        Tuple<Integer,Map<String, Double>> t = new Tuple<Integer,Map<String, Double>>(total,fre_map);
        return t;

    }
    private ArrayList<QueryTuple> genenrateQueryTuple(Question q,List<String> predicatename) {

        ArrayList<QueryTuple> tuples = new ArrayList<>();

        if (q.getQuestionEntity().isEmpty() || q.getQuestionEntity() == null)
            return tuples;
        //从问题中将实体删掉后，去匹配模板;
        for (Entity e : q.getQuestionEntity()) {
            for(String predicat:predicatename)
            {

                Predicate p = new Predicate();
                p.setKgPredicateName(predicat);
                QueryTuple tuple = new QueryTuple();
                QuestionTemplate qTemplate = new QuestionTemplate();
                qTemplate.setPredicate(p);
                qTemplate.setTemplateString(predicat);
                tuple.setTemplate(qTemplate);
                tuple.setSubjectEntity(e);
                tuple.setPredicate(qTemplate.getPredicate());

                tuple.setTupleScore(0.0);

                tuples.add(tuple);
            }
        }

        return tuples;
    }

    private double calTwoWordsSimilarityMethods(String w1, String w2, String methods){
        switch(methods){
            case "word" :
                //语句
                Word2VecGensimModel w2vModel = null;
                try {
                    w2vModel = Word2VecGensimModel.getInstance();
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
                double temp_score = w2vModel.calcWordSimilarity(w1, w2);
                return temp_score;
            case "character" :
                Result res = null;
                try {
                    res = check_words(w1);
                    res = calVec(res);
                    Result presult = check_words(w2);
                    presult = calVec(presult);
                    double sim = Word2VecGensimModel.calcVecSimilarity(res.vec, presult.vec);
                    return sim;
                } catch (IOException e) {
                    e.printStackTrace();
                }

                return 0.;
            case "distance" :
                double ed1 = EditingDistance.getRepetitiveRate(w1, w2);
                double ed2 = EditingDistance.getRepetitiveRate(w2, w1);
                double ed_score = ed1 >= ed2 ? ed1 : ed2;
                return  ed_score;
            default : //可选
                //语句
                return 0.;
        }
    }
}

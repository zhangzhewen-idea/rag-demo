package com.zhangzhewen.ragdemo.domain.gateway;

import com.zhangzhewen.ragdemo.domain.conversation.RetrievedChunk;
import com.zhangzhewen.ragdemo.domain.evaluation.EvaluationModels.Judgment;
import java.util.List;

/**
 * 使用独立 Judge 评估答案忠实度、相关性和关联证据支撑准确率。
 */
public interface EvaluationJudgeGateway {

  Judgment judge(String question, String goldenAnswer, String answerType, String answer,
      List<RetrievedChunk> evidence);
}

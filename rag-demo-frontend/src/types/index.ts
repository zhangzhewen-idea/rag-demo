export interface ApiResponse<T> {
  code: string;
  message: string;
  data: T;
  traceId: string
}

export interface User {
  id: number;
  username: string;
  nickname: string;
  avatarUrl?: string;
  roles: string[];
  status: string
}

export interface KnowledgeBase {
  id: number;
  name: string;
  description?: string;
  coverUrl?: string;
  status: string
}

export interface Conversation {
  id: number;
  userId: number;
  knowledgeBaseId: number;
  title: string;
  status: string
}

export interface Message {
  id: number;
  role: 'USER' | 'ASSISTANT';
  content: string;
  status: string;
  createdAt: string
}

export interface Reference {
  knowledgeBaseId: number;
  documentId: number;
  sourceName: string;
  chunkIndex: number;
  excerpt: string;
  pageNumber?: number;
  sectionTitle?: string;
  vectorScore: number | null;
  bm25Score: number | null;
  fusionScore: number | null;
  rerankScore: number | null
}

export interface DocumentTask {
  id: number;
  knowledgeBaseId: number;
  originalName: string;
  extension: string;
  fileSize: number;
  status: string;
  chunkCount: number;
  retryCount: number;
  failureStage?: string;
  failureReason?: string
}

export interface ChunkingConfig {
  strategy: 'AUTO' | 'CUSTOM';
  separator: string | null;
  maxChunkLength: number;
  overlapLength: number;
  normalizeWhitespace: boolean
}

export interface ChunkPreviewItem {
  index: number;
  content: string;
  characterCount: number;
  overlapCharacters: number;
  pageNumber?: number;
  sectionTitle?: string
}

export interface ChunkPreview {
  configFingerprint: string;
  totalChunks: number;
  previewedChunks: number;
  truncated: boolean;
  statistics: {
    minCharacters: number;
    maxCharacters: number;
    averageCharacters: number;
    shortChunkCount: number
  };
  chunks: ChunkPreviewItem[]
}

export type EvaluationAnswerType = 'FACTUAL' | 'PROCEDURE' | 'COMPARISON' | 'REFUSAL' | 'SUMMARY'
export type EvaluationReviewVerdict = 'ACCURATE' | 'INACCURATE'

export interface ExpectedContext {
  sourceName: string;
  evidenceContains: string
}

export interface EvaluationCase {
  id: number;
  question: string;
  goldenAnswer: string;
  answerType: EvaluationAnswerType;
  critical: boolean;
  expectedContexts: ExpectedContext[]
}

export interface EvaluationDataset {
  id: number;
  knowledgeBaseId: number;
  name: string;
  version: string;
  caseCount: number;
  createdBy: number;
  createdAt: string;
  cases: EvaluationCase[]
}

export interface EvaluationScores {
  candidateHitRate?: number;
  candidateMrr?: number;
  contextRecall?: number;
  contextPrecision?: number;
  faithfulness?: number;
  answerRelevancy?: number;
  evidenceSupportAccuracy?: number;
  noAnswerAccuracy?: number
}

export interface EvaluationThresholds {
  candidateHitRate: number;
  candidateMrr: number;
  contextRecall: number;
  contextPrecision: number;
  faithfulness: number;
  answerRelevancy: number;
  evidenceSupportAccuracy: number;
  noAnswerAccuracy: number
}

export interface RetrievalQuery {
  query: string;
  weight: number
}

export interface RetrievedChunk {
  documentId: number;
  sourceName: string;
  chunkIndex: number;
  excerpt: string;
  pageNumber?: number;
  sectionTitle?: string;
  vectorScore: number | null;
  bm25Score: number | null;
  fusionScore: number | null;
  rerankScore: number | null
}

export interface EvaluationExecution {
  caseId: number;
  answer?: string;
  refused: boolean;
  rewrittenQuery?: string;
  expandedQueries: RetrievalQuery[];
  candidates: RetrievedChunk[];
  finalEvidence: RetrievedChunk[];
  scores?: EvaluationScores;
  judgeRationale?: string;
  promptTokens: number;
  completionTokens: number;
  latencyMs: number;
  errorMessage?: string
}

export interface EvaluationCaseResult {
  id: number;
  runId: number;
  evaluationCase: EvaluationCase;
  execution: EvaluationExecution;
  passed?: boolean;
  reviewVerdict?: EvaluationReviewVerdict;
  reviewComment?: string;
  reviewedBy?: number;
  reviewedAt?: string
}

export interface EvaluationRun {
  id: number;
  datasetId: number;
  baselineRunId?: number;
  status: string;
  configSnapshot: string;
  scores?: EvaluationScores;
  passed: boolean;
  totalCases: number;
  completedCases: number;
  failedCases: number;
  promptTokens: number;
  completionTokens: number;
  latencyMs: number;
  p95LatencyMs: number;
  errorMessage?: string;
  triggeredBy: number;
  startedAt?: string;
  completedAt?: string;
  results: EvaluationCaseResult[]
}

export interface CreateEvaluationDataset {
  knowledgeBaseId: number;
  name: string;
  version: string;
  cases: Array<{
    question: string;
    goldenAnswer: string;
    answerType: EvaluationAnswerType;
    critical: boolean;
    expectedContexts: ExpectedContext[]
  }>
}

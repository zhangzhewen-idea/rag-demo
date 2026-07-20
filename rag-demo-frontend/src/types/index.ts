export interface ApiResponse<T>{code:string;message:string;data:T;traceId:string}
export interface User{id:number;username:string;nickname:string;avatarUrl?:string;roles:string[];status:string}
export interface KnowledgeBase{id:number;name:string;description?:string;coverUrl?:string;status:string}
export interface Conversation{id:number;userId:number;knowledgeBaseId:number;title:string;status:string}
export interface Message{id:number;role:'USER'|'ASSISTANT';content:string;status:string;createdAt:string}
export interface Reference{knowledgeBaseId:number;documentId:number;sourceName:string;chunkIndex:number;similarityScore:number;excerpt:string;pageNumber?:number;sectionTitle?:string}
export interface DocumentTask{id:number;knowledgeBaseId:number;originalName:string;extension:string;fileSize:number;status:string;chunkCount:number;retryCount:number;failureStage?:string;failureReason?:string}

import React, { useState, useRef, useEffect } from "react";
import { sendChatMessage, getChatHistory, startNewChat } from "@/lib/api.ts";
import { ApiError } from "@/lib/apiClient";
import { formatRateLimitMessage } from "@/lib/rateLimit";

type ChatMessage = {
  role: "user" | "assistant";
  message: string;
  timestamp: string;
};

interface ChatbotProps {
  mobileBottomOffsetClass?: string;
}

const Chatbot = ({ mobileBottomOffsetClass = "bottom-24" }: ChatbotProps) => {
  const [isOpen, setIsOpen] = useState(false);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState("");
  const [loading, setLoading] = useState(false);
  const [messagesLoaded, setMessagesLoaded] = useState(false);

  // ------------------------
  // Refs
  // ------------------------
  const chatRef = useRef<HTMLDivElement>(null);
  const messagesRef = useRef<HTMLDivElement>(null);
  const chatButtonRef = useRef<HTMLButtonElement>(null);

  // ------------------------
  // Handlers
  // ------------------------
  const toggleChat = (e: React.MouseEvent<HTMLButtonElement>) => {
    setIsOpen(prev => !prev);
    // Focus input when opening
    setTimeout(() => chatRef.current?.querySelector<HTMLInputElement>("#chat-input")?.focus(), 0);
    (e.currentTarget as HTMLButtonElement).blur();
  };

  const handleStartNewChat = async () => {
    try {
      await startNewChat(); // reset server chat

      // Reset local chat with greeting
      const greeting: ChatMessage = {
        role: "assistant",
        message: "Hi! Your AI tutor is online and full of brainrot. Ask anything about your lessons.",
        timestamp: new Date().toISOString(),
      };
      setMessages([greeting]);
      setInput("");
      setMessagesLoaded(true);
    } catch (error) {
      console.error("Failed to start new chat:", error);
      setMessages([{
        role: "assistant",
        message: "Something went wrong. Could not start a new chat.",
        timestamp: new Date().toISOString(),
      }]);
      setMessagesLoaded(true);
    }
  };

  const handleSend = async () => {
    if (loading) return;

    const validation = validateInput(input);
    if (!validation.valid) {
      setError(validation.error); // or show inline error in chat UI
      return;
    }

    setError("");

    // Append user message
    const userMessage: ChatMessage = { role: "user", message: input, timestamp: new Date().toISOString() };
    setMessages(prev => [...prev, userMessage]);
    setInput("");
    setLoading(true);

    try {
      const res = await sendChatMessage(userMessage.message);
      const botMessage: ChatMessage = { role: "assistant", message: res.reply, timestamp: new Date().toISOString() };
      setMessages(prev => [...prev, botMessage]);
    } catch (error) {
      const message =
        error instanceof ApiError && (error.status === 429 || error.code === "rate_limited")
          ? formatRateLimitMessage(error.retryAfterSeconds)
          : "Something went wrong.";
      const errorMsg: ChatMessage = {
        role: "assistant",
        message,
        timestamp: new Date().toISOString(),
      };
      setMessages(prev => [...prev, errorMsg]);
    }

    setLoading(false);
  };

  // ------------------------
  // Frontend validation
  // ------------------------
  const validateInput = (text: string) => {
    if (!text.trim()) return { valid: false, error: "Message cannot be empty." };
    if (text.length > 250) return { valid: false, error: "Message too long." };
    // Add more checks here if needed (profanity, forbidden words, etc.)
    return { valid: true };
  };

  // ------------------------
  // Effects
  // ------------------------

  // Load chat history when chat opens
  useEffect(() => {
    if (!isOpen) return;

    getChatHistory()
      .then(data => {
        const formatted: ChatMessage[] = data.map(d => ({
          role: d.role === "user" ? "user" : "assistant",
          message: d.message,
          timestamp: d.timestamp,
        }));
        const greeting: ChatMessage = {
          role: "assistant",
          message: "Hi! Your AI tutor is online and full of brainrot. Ask anything about your lessons.",
          timestamp: new Date().toISOString(),
        };
        setMessages([greeting, ...formatted]);
      })
      .catch(() => console.error("Failed to fetch chat history"))
      .finally(() => setMessagesLoaded(true));
  }, [isOpen]);

  // Close chat when clicking outside
  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (!chatRef.current?.contains(event.target as Node) && !chatButtonRef.current?.contains(event.target as Node)) {
        setIsOpen(false);
      }
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  // Auto-scroll to bottom when messages update
  useEffect(() => {
    if (messagesRef.current) messagesRef.current.scrollTop = messagesRef.current.scrollHeight;
  }, [messages]);

  // ------------------------
  // Render
  // ------------------------
  return (
    <>
      {/* Floating chat button */}
      {!isOpen && (
        <button
          ref={chatButtonRef}
          onClick={toggleChat}
          className={`fixed ${mobileBottomOffsetClass} lg:bottom-6 right-6 w-14 h-14 rounded-full flex items-center justify-center
            transition-all duration-300 shadow hover:scale-110 focus:outline-none
            bg-pink-500 text-white`} // darker background and white icon for light mode
          aria-label="Open Chat"
        >
          💬
        </button>
      )}

      {/* Chat window */}
      <div
        ref={chatRef}
        className={`fixed right-6 w-72 h-96
          ${mobileBottomOffsetClass} lg:bottom-6 
          bg-white dark:bg-[#1c1c1e] shadow-lg rounded-xl flex flex-col
          border border-gray-200 dark:border-gray-700 overflow-hidden
          transition-all duration-300 transform
          ${isOpen ? "opacity-100 translate-y-0 pointer-events-auto" : "opacity-0 translate-y-4 pointer-events-none"}`}
      >
        {/* Header */}
        <div className="flex items-center justify-between px-4 py-3 border-b border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-[#2a2a2e]">
          <span className="font-semibold text-sm text-mainAccent dark:text-white">AI Tutor</span>
          <div className="flex items-center gap-3">
            <button onClick={handleStartNewChat} className="text-xs font-medium text-blue-500 hover:text-blue-600">
              New Chat
            </button>
            <button onClick={toggleChat} className="text-gray-400 hover:text-mainAccent dark:hover:text-white text-lg leading-none">✕</button>
          </div>
        </div>
        
        {/* ERROR BANNER */}
        <div className={`transition-all duration-300 overflow-hidden
                        ${error ? "max-h-10 opacity-100" : "max-h-0 opacity-0"}`}>
          <div className="flex items-center justify-between bg-red-100 text-red-600 px-3 py-2 text-sm">
            <span>{error}</span>
            <button
              onClick={() => setError("")}
              className="ml-2 text-red-500 hover:text-red-700"
            >
              ✕
            </button>
          </div>
        </div>

        {/* Messages */}
        <div ref={messagesRef} className="flex-1 p-3 flex flex-col gap-2 overflow-y-scroll scrollbar-thin scrollbar-thumb-gray-300 dark:scrollbar-thumb-gray-600">
          {messagesLoaded ? (
            messages.map((msg, idx) => (
              <div
                key={idx}
                className={`px-4 py-2 rounded-2xl max-w-[75%] text-sm break-words shadow-sm
                           ${msg.role === "user"
                             ? "self-end bg-mainAccent text-white dark:bg-[#d6336c] dark:text-white"
                             : "self-start bg-gray-100 text-gray-900 dark:bg-gray-700 dark:text-white"}`}
              >
                {msg.message.replace(/^"(.*)"$/, '$1')}
              </div>
            ))
          ) : (
            <div className="self-start bg-gray-100 dark:bg-gray-700 px-4 py-2 rounded-2xl text-sm text-gray-500 dark:text-gray-300 animate-pulse">
              Loading...
            </div>
          )}
          {loading && (
            <div className="self-start bg-gray-100 dark:bg-gray-700 px-4 py-2 rounded-2xl text-sm text-gray-500 dark:text-gray-300 animate-pulse">
              AI is thinking...
            </div>
          )}
        </div>
        
        {/* Input area */}
        <div className="flex flex-col p-2 border-t border-gray-200 dark:border-gray-700 gap-1">
          <div className="flex gap-2">
            <textarea
              id="chat-input"
              value={input}
              onChange={e => setInput(e.target.value.slice(0, 250))}
              onKeyDown={e => {
                if (e.key === "Enter" && !e.shiftKey) {
                  e.preventDefault();
                  if (input.trim() && !loading) handleSend();
                }
              }}
              placeholder="Type a message..."
              className="flex-1 resize-none rounded-xl border border-gray-300 dark:border-gray-600
                        px-4 py-2 text-sm focus:outline-none focus:ring-1 focus:ring-mainAccent
                        bg-white dark:bg-[#2a2a2e] text-gray-900 dark:text-white
                        max-h-[4.5rem] overflow-y-auto"
              rows={2}
            />
            <button
              onClick={handleSend}
              disabled={loading || input.trim() === ""}
              className={`px-3 py-2 rounded-full text-sm flex-shrink-0
                        ${loading || input.trim() === ""
                          ? "bg-gray-400 cursor-not-allowed text-gray-200"
                          : "bg-[#ff5c8d] hover:bg-[#d6336c] text-white"}`}
            >
              ➤
            </button>
          </div>
        </div>
      </div>
    </>
  );
};

export default Chatbot;

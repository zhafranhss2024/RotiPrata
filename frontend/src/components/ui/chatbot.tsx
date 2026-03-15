import React, { useState, useRef, useEffect } from "react";
import { Input } from "./input.tsx";
import { sendChatMessage, getChatHistory, startNewChat } from "@/lib/api.ts";

type ChatMessage = {
  role: "user" | "assistant";
  message: string;
  timestamp: string;
};

const Chatbot = () => {
  const [isOpen, setIsOpen] = useState(false);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState("");
  const [loading, setLoading] = useState(false);
  const [messagesLoaded, setMessagesLoaded] = useState(false);

  const chatRef = useRef<HTMLDivElement>(null);
  const messagesRef = useRef<HTMLDivElement>(null);
  const chatButtonRef = useRef<HTMLButtonElement>(null);

  // Toggle chat window
  const toggleChat = (e: React.MouseEvent<HTMLButtonElement>) => {
    setIsOpen(prev => !prev);
    setTimeout(() => {
      const inputEl = document.getElementById("chat-input") as HTMLInputElement;
      inputEl?.focus();
    }, 0);
    (e.currentTarget as HTMLButtonElement).blur();
  };

  // Start new chat
  const handleStartNewChat = async () => {
    try {
      await startNewChat();
      setMessages([]);
      setInput("");
      setMessagesLoaded(false);
    } catch (error) {
      console.error("Failed to start new chat:", error);
    }
  };

  // Send user message
  const handleSend = async () => {
    if (!input.trim() || loading) return;

    const question = input;
    const userMessage: ChatMessage = {
      role: "user",
      message: question,
      timestamp: new Date().toISOString(),
    };

    setMessages(prev => [...prev, userMessage]);
    setInput("");
    setLoading(true);

    try {
      const res = await sendChatMessage(question);
      const botMessage: ChatMessage = {
        role: "assistant",
        message: res.reply,
        timestamp: new Date().toISOString(),
      };
      setMessages(prev => [...prev, botMessage]);
    } catch {
      const errorMsg: ChatMessage = {
        role: "assistant",
        message: "Something went wrong.",
        timestamp: new Date().toISOString(),
      };
      setMessages(prev => [...prev, errorMsg]);
    }

    setLoading(false);
  };

  // Fetch chat history on open
  useEffect(() => {
    if (isOpen) {
      getChatHistory()
        .then(data => {
          const formatted: ChatMessage[] = data.map(d => ({
            role: d.role === "user" ? "user" : "assistant",
            message: d.message,
            timestamp: d.timestamp,
          }));

          const greeting: ChatMessage = {
            role: "assistant",
            message:
              "Hi! Your AI tutor is online and full of brainrot. Ask anything about your lessons.",
            timestamp: new Date().toISOString(),
          };

          setMessages([greeting, ...formatted]);
        })
        .catch(() => console.error("Failed to fetch chat history"))
        .finally(() => setMessagesLoaded(true));
    }
  }, [isOpen]);

  // Close chat when clicking outside
  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (
        chatRef.current &&
        !chatRef.current.contains(event.target as Node) &&
        chatButtonRef.current &&
        !chatButtonRef.current.contains(event.target as Node)
      ) {
        setIsOpen(false);
      }
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  // Auto-scroll to bottom
  useEffect(() => {
    const container = messagesRef.current;
    if (container) {
      container.scrollTop = container.scrollHeight;
    }
  }, [messages]);

  return (
    <>
      {/* Floating Chat Icon */}
      {!isOpen && (
        <button
          ref={chatButtonRef}
          onClick={toggleChat}
          className={`fixed bottom-24 lg:bottom-6 right-6 w-14 h-14 rounded-full flex items-center justify-center
            transition-all duration-300 shadow hover:scale-110 focus:outline-none
            bg-pink-500 text-white`} // darker background and white icon for light mode
          aria-label="Open Chat"
        >
          💬
        </button>
      )}

      {/* Chat Window */}
      <div
        ref={chatRef}
        className={`fixed right-6 w-72 h-96
          bottom-24 lg:bottom-6 
          bg-white dark:bg-[#1c1c1e] shadow-lg rounded-xl flex flex-col
          border border-gray-200 dark:border-gray-700 overflow-hidden
          transition-all duration-300 transform
          ${isOpen ? "opacity-100 translate-y-0 pointer-events-auto" : "opacity-0 translate-y-4 pointer-events-none"}`}
      >
        {/* Header */}
        <div className="flex items-center justify-between px-4 py-3 border-b border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-[#2a2a2e]">
          <span className="font-semibold text-sm text-mainAccent dark:text-white">
            AI Tutor
          </span>
          <div className="flex items-center gap-3">
            <button
              onClick={handleStartNewChat}
              className="text-xs font-medium text-blue-500 hover:text-blue-600"
            >
              New Chat
            </button>
            <button
              onClick={toggleChat}
              className="text-gray-400 hover:text-mainAccent dark:hover:text-white text-lg leading-none"
            >
              ✕
            </button>
          </div>
        </div>

        {/* Messages */}
        <div
          ref={messagesRef}
          className="flex-1 p-3 flex flex-col gap-2 overflow-y-scroll scrollbar-thin scrollbar-thumb-gray-300 dark:scrollbar-thumb-gray-600"
        >
          {messagesLoaded ? (
            messages.map((msg, idx) => (
              <div
                key={idx}
                className={`px-4 py-2 rounded-2xl max-w-[75%] text-sm break-words shadow-sm
                  ${msg.role === "user"
                    ? "self-end bg-mainAccent text-white dark:bg-[#d6336c] dark:text-white"
                    : "self-start bg-gray-100 text-gray-900 dark:bg-gray-700 dark:text-white"
                  }`}
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

        {/* Input */}
        <div className="flex p-2 border-t border-gray-200 dark:border-gray-700 gap-2">
          <textarea
            id="chat-input"
            value={input}
            onChange={e => {
              if (e.target.value.length <= 250) { // limit to 250 chars
                setInput(e.target.value);
              }
            }}
            onKeyDown={e => e.key === "Enter" && !e.shiftKey && handleSend()}
            placeholder="Type a message..."
            className="flex-1 resize-none rounded-xl border border-gray-300 dark:border-gray-600
                      px-4 py-2 text-sm focus:outline-none focus:ring-1 focus:ring-mainAccent
                      bg-white dark:bg-[#2a2a2e] text-gray-900 dark:text-white
                      max-h-[4.5rem] overflow-y-auto"
            rows={2}
          />
          <button
            onClick={handleSend}
            disabled={loading}
            className="bg-[#ff5c8d] hover:bg-[#d6336c] text-white px-3 py-2 rounded-full text-sm flex-shrink-0"
          >
            ➤
          </button>
        </div>
      </div>
    </>
  );
};

export default Chatbot;
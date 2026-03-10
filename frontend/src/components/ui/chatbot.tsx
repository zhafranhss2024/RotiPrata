import React, { useState, useRef, useEffect } from "react";
import { Input } from "./input.tsx"
import { sendChatMessage } from "@/lib/api.ts"

type ChatMessage = {
  sender: "user" | "bot";
  text: string;
};

const Chatbot = () => {
  const [isOpen, setIsOpen] = useState(false);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState("");
  const [loading, setLoading] = useState(false);
  const chatRef = useRef<HTMLDivElement>(null);
  const messagesRef = useRef<HTMLDivElement>(null);

  const toggleChat = (e: React.MouseEvent<HTMLButtonElement>) => {
    setIsOpen(prev => !prev);
    setTimeout(() => {
      const inputEl = document.getElementById("chat-input") as HTMLInputElement;
      inputEl?.focus();
    }, 0);
    (e.currentTarget as HTMLButtonElement).blur(); // remove focus from button
  };

  const handleSend = async () => {
    if (!input.trim() || loading) return;

    setLoading(true);

    const userMessage: ChatMessage = { sender: "user", text: input };
    const updatedMessages = [...messages, userMessage];

    setMessages(updatedMessages);
    setInput("");

    try {
      const res = await sendChatMessage(
        updatedMessages.map(m => ({
          role: m.sender === "user" ? "user" : "assistant",
          content: m.text
        }))
      );

      setMessages(prev => [
        ...prev,
        { sender: "bot", text: res.reply }
      ]);
    } catch {
      setMessages(prev => [
        ...prev,
        { sender: "bot", text: "Something went wrong." }
      ]);
    }

    setLoading(false);
  };

  useEffect(() => {
    if (isOpen && messages.length === 0) { 
      const timer = setTimeout(() => {
        if (!isOpen) return;
        setMessages([
          { sender: "bot", text: "Hi! Your AI tutor is online and full of brainrot. Ask anything about your lessons, I dare you." }
        ]);
      }, 200); 
      return () => clearTimeout(timer); 
    }
  }, [isOpen, messages.length]);

  const chatButtonRef = useRef<HTMLButtonElement>(null);

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

  useEffect(() => {
    const container = messagesRef.current;
    if (container) {
      container.scrollTop = container.scrollHeight;
    }
  }, [messages]);

  return (
    <>
      {/* Floating Chat Icon */}
      <button
        ref={chatButtonRef}
        onClick={toggleChat}
        className={`fixed bottom-6 right-6 w-14 h-14 rounded-full flex items-center justify-center
                    transition-all duration-300 shadow hover:scale-110 focus:outline-none
                    ${isOpen ? "bg-mainAccent text-white" : "bg-mainAlt text-mainAccent"}`}
        aria-label="Open Chat"
      >
        💬
      </button>

      {/* Chat Window */}
      {isOpen && (
        <div
          ref={chatRef}
          className="fixed bottom-[96px] right-6 w-72 h-96 bg-white dark:bg-[#1c1c1e] shadow-lg rounded-xl flex flex-col border border-gray-200 dark:border-gray-700 overflow-hidden"
        >
          {/* Header */}
          <div className="flex items-center justify-between p-3 border-b border-gray-200 dark:border-gray-700">
            <span className="font-medium text-sm text-mainAccent dark:text-white">Chat</span>
            <button
              onClick={toggleChat}
              className="text-gray-400 hover:text-mainAccent dark:hover:text-white font-bold"
            >
              ✕
            </button>
          </div>

          {/* Messages */}
          <div
            ref={messagesRef}
            className="flex-1 p-3 flex flex-col gap-2 overflow-y-auto scrollbar-thin scrollbar-thumb-gray-300 dark:scrollbar-thumb-gray-600"
          >
            {messages.map((msg, idx) => (
              <div
                key={idx}
                className={`px-3 py-2 rounded-xl max-w-[75%] text-sm break-words
                  ${msg.sender === "user"
                    ? "self-end bg-mainAccent text-white dark:bg-[#d6336c] dark:text-white"
                    : "self-start bg-gray-100 text-gray-900 dark:bg-gray-700 dark:text-white"
                  }`}
              >
                {msg.text}
              </div>
            ))}
          </div>

          {/* Input */}
          <div className="flex p-2 border-t border-gray-200 dark:border-gray-700 gap-2">
            <Input
              id="chat-input"
              type="text"
              value={input}
              onChange={e => setInput(e.target.value)}
              onKeyDown={e => e.key === "Enter" && handleSend()}
              placeholder="Type a message..."
            />
            <button
              onClick={handleSend}
              disabled={loading}
              className="bg-mainAccent text-white px-3 py-1 rounded-full text-sm hover:bg-[#d6336c] transition-colors"
            >
              Send
            </button>
          </div>
        </div>
      )}
    </>
  );
};

export default Chatbot;
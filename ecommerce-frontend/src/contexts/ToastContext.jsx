import React, { createContext, useContext, useState, useCallback, useMemo, useRef } from 'react';

const ToastContext = createContext(null);

export function ToastProvider({ children }) {
  const [messages, setMessages] = useState([]);
  const nextId = useRef(0);

  const remove = useCallback((id) => {
    // First set leaving to true
    setMessages((prev) => 
      prev.map(m => m.id === id ? { ...m, leaving: true } : m)
    );
    // Then remove after animation completes (200ms)
    setTimeout(() => {
      setMessages((prev) => prev.filter((m) => m.id !== id));
    }, 200);
  }, []);

  const add = useCallback((text, type = 'success') => {
    const id = ++nextId.current;
    setMessages((prev) => [...prev, { id, text, type, leaving: false }]);
    setTimeout(() => {
      remove(id);
    }, 5000);
  }, [remove]);

  const success = useCallback((text) => add(text, 'success'), [add]);
  const error = useCallback((text) => add(text, 'error'), [add]);
  const info = useCallback((text) => add(text, 'info'), [add]);

  // Stable identity: components list `toast` in effect dependencies, and a new object per message
  // would re-run their data loading (wiping half-filled forms) every time a toast appears.
  const api = useMemo(() => ({ success, error, info, remove }), [success, error, info, remove]);

  return (
    <ToastContext.Provider value={api}>
      {children}
      <div className="toast-stack" aria-live="polite">
        {messages.map((msg) => (
          <button
            key={msg.id}
            className={`toast-message ${msg.type === 'error' ? 'error' : ''} ${msg.type === 'info' ? 'info' : ''} ${msg.leaving ? 'leaving' : ''}`}
            type="button"
            onClick={() => remove(msg.id)}
          >
            {msg.text}
          </button>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast() {
  return useContext(ToastContext);
}

import React from 'react';
import { BrandMark } from './BrandMark';

export function Loader({ fullPage = false, message = "Loading..." }) {
  const loaderContent = (
    <div className="loader-content rs-loader" role="status">
      <BrandMark draw />
      <p>{message}</p>
    </div>
  );

  if (fullPage) {
    return <div className="loader-fullpage">{loaderContent}</div>;
  }

  return <div className="loader-container">{loaderContent}</div>;
}

export function Skeleton({ width = '100%', height = '20px', borderRadius = '8px' }) {
  return (
    <div 
      className="skeleton-loader" 
      style={{ width, height, borderRadius }}
    ></div>
  );
}

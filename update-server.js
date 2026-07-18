import { createServer } from 'http';

const versionConfig = {
  version: '2.0.0',
  apkUrl: 'https://gofile.io/d/JlEjjU'
};

const server = createServer((req, res) => {
  res.setHeader('Content-Type', 'application/json');
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type');
  
  if (req.method === 'OPTIONS') {
    res.writeHead(200);
    res.end();
    return;
  }
  
  if (req.url === '/version' && req.method === 'GET') {
    res.writeHead(200);
    res.end(JSON.stringify(versionConfig));
    return;
  }
  
  res.writeHead(404);
  res.end(JSON.stringify({ error: 'Not found' }));
});

server.listen(3000, () => {
  console.log('Version server running on http://localhost:3000');
});

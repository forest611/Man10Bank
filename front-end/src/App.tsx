import React, { useState } from 'react';

const App: React.FC = () => {
  const [uuid, setUuid] = useState<string>('');
  const [balance, setBalance] = useState<string | null>(null);

  const handleInputChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    console.log(event.target.value)
    setUuid(event.target.value);
  };

  const fetchBalance = async () => {

    console.log(encodeURIComponent(uuid))

    try {
      const response = await fetch(`http://cortex.jp:6110/bank/balance?uuid=${encodeURIComponent(uuid)}`);
      if (!response.ok) {
        throw new Error('Network response was not ok');
      }

      const data = await response.text();

      console.log(`Balance:${data}`)
      setBalance(data);
    } catch (error) {
      console.error('Error fetching balance:', error);
    }
  };

  return (
      <div>
        <h1>Get Balance</h1>
        <div>
          <label htmlFor="uuidInput">Enter UUID:</label>
          <input
              type="text"
              id="uuidInput"
              value={uuid}
              onChange={handleInputChange}
          />
          <button onClick={fetchBalance}>Get Balance</button>
        </div>
        {balance !== null ? (
            <div>
              <p>Balance: {balance}</p>
            </div>
        ) : null}
      </div>
  );
};

export default App;
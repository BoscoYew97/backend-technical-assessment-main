# Trade Execution API Implementation

Here is a summary of the components implemented:

## DTOs

- **TradeRequest**: Added `pairName` for crypto pair and the `quantity` of the base currency to be traded.
- **TradeResponse**: Added complete details of an executed trade, including the transaction `tradeId`, `userId`, `pairName`, `tradeType`, `quantity`, `price`, `totalAmount`, `tradeTime`, and a `status` field.

## Mappers

- **UserWalletMapper**: 
  - Added `updateBalance()` to update balance whether for increment or decrement.
  - Added `findByUserIdAndSymbolId()` to check for wallet existence.
  - Added `insert()` to create wallet when a user trades the symbol for the first time.
- **CryptoPairMapper**: Added `findByPairName` to fetch crypto pair details.
- **TradeMapper**: Added `insert()` to insert trading records.
- **UserMapper**: Added `findByUserId()` to check user existence.

## Core Business Logic

- **TradeServiceImpl**: updated `executeTrade` method:
    1. Validates the incoming trade request.
    2. Fetch active crypto pair.
    3. Fetch latest best price.
    4. Determine bid/ask price and calculate total amount. 
    5. Validate, update wallet balances and create new wallet if none exist.
    6. Save trade record and return response.

## Error Handling

- Created an `exception` package to handle specific runtime errors: `InsufficientBalanceException`, `InvalidTradeException`, and `ResourceNotFoundException`.
- Built a **GlobalExceptionHandler** with `@RestControllerAdvice`. 
  - Intercepts these errors and returns responses with the appropriate HTTP status codes (400 Bad Request, 404 Not Found) instead of generic server errors.

## Mock Testing

- **TradeServiceImplTest**: Added mock testing for `BUY` and `SELL`.

## Verification
You can now start the application using your IDE or command line, open the Swagger UI (`http://localhost:8080/swagger-ui/index.html`), and start executing `BUY` and `SELL` requests to the system!

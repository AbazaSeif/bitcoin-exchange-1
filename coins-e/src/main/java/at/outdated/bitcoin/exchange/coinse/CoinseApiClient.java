package at.outdated.bitcoin.exchange.coinse;

import at.outdated.bitcoin.exchange.api.OrderId;
import at.outdated.bitcoin.exchange.api.account.Balance;
import at.outdated.bitcoin.exchange.api.account.WalletTransaction;
import at.outdated.bitcoin.exchange.api.client.RestExchangeClient;
import at.outdated.bitcoin.exchange.api.currency.Currency;
import at.outdated.bitcoin.exchange.api.currency.CurrencyAddress;
import at.outdated.bitcoin.exchange.api.currency.CurrencyValue;
import at.outdated.bitcoin.exchange.api.market.*;
import at.outdated.bitcoin.exchange.api.market.fee.SimplePercentageFee;
import at.outdated.bitcoin.exchange.coinse.jaxb.CoinseOrder;
import at.outdated.bitcoin.exchange.coinse.jaxb.DepositAddress;
import at.outdated.bitcoin.exchange.coinse.jaxb.ListOrders;
import at.outdated.bitcoin.exchange.coinse.jaxb.SingleOrder;
import org.apache.commons.codec.binary.Hex;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Form;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by ebirn on 06.01.14.
 */
public class CoinseApiClient extends RestExchangeClient {

    WebTarget baseTarget, marketTarget;
    public CoinseApiClient(Market market) {
        super(market);

        tradeFee = new SimplePercentageFee("0.002");
        baseTarget = client.target("https://www.coins-e.com/api/v2/");
        marketTarget = baseTarget.path("/market/{base}_{quote}/");
    }

    @Override
    protected <T> Invocation.Builder setupProtectedResource(WebTarget res, Entity<T> entity) {



        Form form = ((Entity<Form>) entity).getEntity();
        form.param("nonce", Long.toString((new Date()).getTime()));

        if(form.asMap().get("method") == null) {
            log.warn("form value 'method' is not set");
        }

        String hexSignature = null;
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            SecretKeySpec secret_spec = new SecretKeySpec(getSecret().getBytes("UTF-8"), "HmacSHA512");
            mac.init(secret_spec);
            mac.update(formData2String(form).getBytes());

            hexSignature = Hex.encodeHexString(mac.doFinal()).toLowerCase();
        }
        catch(Exception e) {
            log.error("failed to setup hashing");
        }

        Invocation.Builder builder = res.request();

        // documentation is wrong, this must be headers!
        builder.header("key", getUserId());
        builder.header("sign", hexSignature);

        return builder;
    }

    @Override
    public TickerValue getTicker(AssetPair asset) {

        // https://www.coins-e.com/api/v2/markets/data/

        String raw = simpleGetRequest(baseTarget.path("markets/data/"), String.class);

        JsonObject root = jsonFromString(raw);

        JsonObject jsonMarketList = root.getJsonObject("markets");

        String marketKey = asset.getBase() + "_" + asset.getQuote();
        JsonObject jsonMarket = jsonMarketList.getJsonObject(marketKey);

        JsonObject marketStat = jsonMarket.getJsonObject("marketstat");
        JsonObject stat24 = marketStat.getJsonObject("24h");

        TickerValue ticker = new TickerValue(asset);

        Currency quote = asset.getQuote();
        ticker.setLast(new CurrencyValue(marketStat.getString("ltp"), quote));
        ticker.setAsk(new CurrencyValue(marketStat.getString("ask"), quote));
        ticker.setBid(new CurrencyValue(marketStat.getString("bid"), quote));

        ticker.setHigh(new CurrencyValue(stat24.getString("h"), quote));
        ticker.setLow(new CurrencyValue(stat24.getString("l"), quote));
        ticker.setVolume(new CurrencyValue(stat24.getString("volume"), asset.getBase()));

        return ticker;
    }

    @Override
    public MarketDepth getMarketDepth(AssetPair asset) {

        // https://www.coins-e.com/api/v2/market/WDC_BTC/depth/
        String raw = simpleGetRequest(marketTarget.path("/depth/").resolveTemplate("base", asset.getBase().name()).resolveTemplate("quote", asset.getQuote().name()), String.class);
        JsonObject root = jsonFromString(raw);

        // {"status": true, "ltq": "31.07186789", "ltp": "0.00052500", "marketdepth": {"bids": [{"q": "67.61970

        JsonObject jsonDepth = root.getJsonObject("marketdepth");

        MarketDepth depth = new MarketDepth(asset);

        // are these mixed up in the api / exchange site?

        JsonArray jsonAsks = jsonDepth.getJsonArray("asks");
        // beginning lowest ask
        for(int i=0; i<jsonAsks.size(); i++) {
            JsonObject obj = jsonAsks.getJsonObject(i);
            BigDecimal price = new BigDecimal(obj.getString("r"));
            BigDecimal volume = new BigDecimal(obj.getString("q"));

            if(obj.getInt("n") > 0) {
                depth.addAsk(volume, price);
            }
        }

        JsonArray jsonBids = jsonDepth.getJsonArray("bids");
        // beginning with highest bid
        for(int i=0; i<jsonBids.size(); i++) {
            JsonObject obj = jsonBids.getJsonObject(i);
            BigDecimal price = new BigDecimal(obj.getString("r"));
            BigDecimal volume = new BigDecimal(obj.getString("q"));

            if(obj.getInt("n") > 0) {
                depth.addBid(volume, price);
            }
        }

        return depth;
    }

    @Override
    public List<MarketOrder> getTradeHistory(AssetPair asset, Date since) {

        // https://www.coins-e.com/api/v2/market/WDC_BTC/trades/
        WebTarget tgt = marketTarget.path("/trades/").resolveTemplate("base", asset.getBase().name()).resolveTemplate("quote", asset.getQuote().name());

        ListOrders result = simpleGetRequest(tgt, ListOrders.class);

        List<MarketOrder> history = null;

        if(result != null && result.isSuccess()) {
            history = new ArrayList<>();
            for(CoinseOrder co : result.getOrders()) {
                if(since.before(co.getCreated())) {
                    history.add(co.getOrder(market));
                }
            }

        }
        else {
            if(result != null) {
                log.error("failed to load trade history: {}", result.getMessage());
            }
            else {
                log.error("failed to http request for trade history");
            }
        }

        return history;
    }

    @Override
    public List<WalletTransaction> getTransactions() {

        // FIXME
        // TODO: reuse getOpenOrders??

        log.warn("transaction list not supported by api");

        return new ArrayList<>();
    }

    @Override
    public Balance getBalance() {

        WebTarget balanceTarget = baseTarget.path("wallet/all/");

        Form form = new Form();
        form.param("method", "getwallets");

        String rawBalance = protectedPostRequest(balanceTarget, String.class, Entity.form(form));

        JsonObject jsonBalance = jsonFromString(rawBalance);

        Balance balance = null;
        if(jsonBalance.getBoolean("status")) {

            JsonObject jsonWallets = jsonBalance.getJsonObject("wallets");

            balance = new Balance(market);

            for(Currency curr : market.getCurrencies()) {

                String key = curr.name();

                if(jsonWallets.containsKey(key)) {
                    JsonObject jsonWallet = jsonWallets.getJsonObject(key);

                    BigDecimal a = new BigDecimal(jsonWallet.getString("a"), CurrencyValue.CURRENCY_MATH_CONTEXT);
                    BigDecimal h = new BigDecimal(jsonWallet.getString("h"), CurrencyValue.CURRENCY_MATH_CONTEXT);
                    BigDecimal u = new BigDecimal(jsonWallet.getString("u"), CurrencyValue.CURRENCY_MATH_CONTEXT);

                    balance.setAvailable(new CurrencyValue(a, curr));
                    balance.setOpen((new CurrencyValue(h.add(u), curr)));
                }
            }

        }
        else {
            log.error("failed to load balance: {}", jsonBalance.getString("message"));
            balance = null;
        }

        return balance;
    }

    // FIXME
    @Override
    public List<MarketOrder> getOpenOrders() {

        // https://www.coins-e.com/api/v2/market/WDC_BTC/
        WebTarget openOrdersTgt = baseTarget.path("market/{base}_{quote}/");

        //List<Future<ListOrders>> results = new ArrayList<>();
        List<MarketOrder> allOrders = new ArrayList<>();

        for(AssetPair asset : market.getTradedAssets()) {

            WebTarget assetTarget = openOrdersTgt.resolveTemplate("base", asset.getBase().name())
                                                 .resolveTemplate("quote", asset.getQuote().name());
            Form form = new Form();
            form.param("method", "listorders");
            form.param("filter", "active");
            form.param("limit", "100");

            Entity entity = Entity.form(form);
            ListOrders result = protectedPostRequest(assetTarget, ListOrders.class, entity);

            if(result.isSuccess()) {
                for(CoinseOrder rawOrder : result.getOrders()) {

                    MarketOrder order = rawOrder.getOrder(market);

                    // just to be safe, asset value in order is parsed from api response
                    assert(asset.equals(order.getAsset()));

                    allOrders.add(order);
                }
            }
            else {
                log.error("error: {}", result.getMessage());
                allOrders = null;
                break;
            }
        }

        return allOrders;
    }

    @Override
    public OrderId placeOrder(AssetPair asset, OrderType type, CurrencyValue volume, CurrencyValue price) {
        WebTarget placeOrderTarget = marketTarget.resolveTemplate("base", asset.getBase().name()).resolveTemplate("quote", asset.getQuote().name());

        Form form = new Form();
        form.param("method", "neworder");

        switch(type) {
            case BID:
                form.param("order_type", "buy");
                break;

            case ASK:
                form.param("order_type", "sell");
                break;

            default:
                form.param("order_type", "ERROR");
        }

        form.param("rate", price.valueToString());
        form.param("quantity", volume.valueToString());

        SingleOrder resultOrder = protectedPostRequest(placeOrderTarget, SingleOrder.class, Entity.form(form));

        return resultOrder.getOrder().getOrder(market).getId();
    }

    @Override
    public boolean cancelOrder(OrderId order) {


        WebTarget cancelTargetBase = baseTarget.path("market/{base}_{quote}/");

        boolean success = false;
        for(AssetPair asset : market.getTradedAssets()) {

            WebTarget cancelTarget = cancelTargetBase.resolveTemplate("base", asset.getBase().name())
                                                     .resolveTemplate("quote", asset.getQuote().name());
            Form form = new Form();
            form.param("method", "cancelorder");
            form.param("order_id", order.getIdentifier());

            SingleOrder resultOrder = protectedPostRequest(cancelTarget, SingleOrder.class, Entity.form(form));

            if(resultOrder.isSuccess()) {
                success = true;
                break;
            }

        }

        if(!success) {
            log.error("failed to cancel order: {}", order);
        }

        return success;
    }

    @Override
    protected CurrencyAddress lookupUpDepositAddress(Currency curr) {
        /*
        {
          "status": true,
          "message": "success",
          "deposit_address": "1Q1RaxqZipDgaUg4r7KYqgoftZGhba1CyV",
          "systime": 1372852975
        }
        */

        // https://www.coins-e.com/api/v2/wallet/BTC/

        WebTarget addressTarget = baseTarget.path("wallet/{curr}/").resolveTemplate("curr", curr.name());
        Form form = new Form();
        form.param("method", "getdepositaddress");

        DepositAddress address = protectedPostRequest(addressTarget, DepositAddress.class, Entity.form(form));

        return new CurrencyAddress(curr, address.getDepositAddress());
    }
}

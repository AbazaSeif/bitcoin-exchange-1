import at.outdated.bitcoin.exchange.api.ExchangeApiClient;
import at.outdated.bitcoin.exchange.api.Market;
import at.outdated.bitcoin.exchange.api.account.AccountInfo;
import at.outdated.bitcoin.exchange.api.currency.Currency;
import at.outdated.bitcoin.exchange.api.market.MarketDepth;
import at.outdated.bitcoin.exchange.api.market.TickerValue;
import at.outdated.bitcoin.exchange.mtgox.MtGoxClient;
import at.outdated.bitcoin.exchange.mtgox.MtGoxMarket;
import org.junit.Assert;
import org.junit.Test;

/**
 * Created with IntelliJ IDEA.
 * User: ebirn
 * Date: 26.05.13
 * Time: 17:24
 * To change this template use File | Settings | File Templates.
 */
public class MtgoxTest {


    MtGoxClient client = new MtGoxClient(new MtGoxMarket());


    @Test
    public void testTicker() {
        TickerValue ticker = client.getTicker(Currency.EUR);

        Assert.assertNotNull(ticker);

    }

    @Test
    public void testAccountInfo() {

        AccountInfo info = client.getAccountInfo();
        Assert.assertNotNull(info);
    }


    @Test
    public void testMarketDepth() {

        MarketDepth depth = client.getMarketDepth(Currency.BTC, Currency.EUR);

        Assert.assertNotNull(depth);
    }
}

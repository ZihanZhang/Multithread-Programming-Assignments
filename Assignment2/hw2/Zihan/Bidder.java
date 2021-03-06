package Zihan;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Random;

public class Bidder implements Client
{
    private String name;
    private int cash;
    private int cycles;
    private int maxSleepTimeMs;
    private int initialCash;

    private Random rand;
    
    private AuctionServer server;
    
    private int mostItemsAvailable = 0;

    public Bidder(AuctionServer server, String name, int cash, int cycles, int maxSleepTimeMs, long randomSeed)
    {
        this.name = name;
        this.cash = cash;
        this.cycles = cycles;
        this.maxSleepTimeMs = maxSleepTimeMs;
        this.initialCash = cash;

        this.rand = new Random(randomSeed);
        
        this.server = server;
    }
    
    public int cash()
    {
        return this.cash;
    }
    
    public int cashSpent()
    {
        return this.initialCash - this.cash;
    }
    
    public int mostItemsAvailable()
    {
        return this.mostItemsAvailable;
    }

    @Override
    public String name()
    {
        return this.name;
    }

    @Override
    public void run()
    {
        List<Item> activeBids = new ArrayList<Item>();
        Hashtable<Item, Integer> activeBidPrices = new Hashtable<Item, Integer>();
        int sumActiveBids = 0;

        for (int i = 0; (i < cycles && cash > 0) || activeBids.size() > 0; ++i)
        {
//            System.out.println("cycle: " + i);
            List<Item> items = server.getItems();
//            System.out.println("items left: " + items.size());
            if (items.size() > this.mostItemsAvailable) { this.mostItemsAvailable = items.size(); }

            while (items.size() > 0)
            {
                
                int index = rand.nextInt(items.size());

                Item item = items.get(index);
                items.remove(index);

                int price = server.itemPrice(item.listingID());
                if (price < this.cash - sumActiveBids)
                {
                    // The server should ensure thread safety: if the price
                    // has already increased, then this bid should be invalid.
                    boolean success = server.submitBid(this.name(), item.listingID(), price + 1);

                    if (success)
                    {
                        if (!activeBidPrices.containsKey(item))
                        {
                            activeBids.add(item);
                        }
                        else
                        {
                            sumActiveBids -= activeBidPrices.get(item);
                        }
                        
                        sumActiveBids += price + 1;
                        activeBidPrices.put(item, price + 1);
                    }
                    break;
                }

                continue;
            }

            List<Item> newActiveBids = new ArrayList<Item>();
            Hashtable<Item, Integer> newActiveBidPrices = new Hashtable<Item, Integer>();
//            System.out.println("size: " + activeBids.size());
            for (Item bid : activeBids)
            {
//                System.out.println("status: " + server.checkBidStatus(this.name(), bid.listingID()));
                switch (server.checkBidStatus(this.name(), bid.listingID()))
                {
                    case 1:
                        // Success
                        int finalPrice = activeBidPrices.get(bid);
                        this.cash -= finalPrice;
                        sumActiveBids -= finalPrice;
//                        System.out.println("Success! revenue: " + server.revenue());

                        break;

                    case 2:
                        // Open
//                        System.out.println("Item's still open");
                        newActiveBids.add(bid);
                        newActiveBidPrices.put(bid, activeBidPrices.get(bid));
                        break;

                    case 3:
                        // Failed
//                        System.out.println("Check failed");
                        sumActiveBids -= activeBidPrices.get(bid);
                        break;

                    default:
                        // Error
                        break;
                }
            }

            activeBids = newActiveBids;
            activeBidPrices = newActiveBidPrices;
            
            try
            {
                Thread.sleep((long)rand.nextInt(this.maxSleepTimeMs));
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }
        }
    }

}
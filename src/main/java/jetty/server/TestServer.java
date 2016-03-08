package jetty.server;

public class TestServer
{
  public static void main(String[] args)
  {
    Object lock1 = new Object();
    Object lock2 = new Object();
    System.out.println("Created locks " + lock1 + " " + lock2);
    synchronized(lock1)
    {
      System.out.println("Entered lock1's first synchronization block.");
      synchronized(lock1)
      {
        System.out.println("Entered lock1's second synchronization block.");
      }
    }
  }
}

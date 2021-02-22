public class GenID {
    public static void main(String[] args){
        System.out.println(System.currentTimeMillis());
        System.out.println("__");
        IdWorker getID = new IdWorker();
        for (int n =0; n <=100; n++)
            System.out.println(getID.nextId());
    }
}
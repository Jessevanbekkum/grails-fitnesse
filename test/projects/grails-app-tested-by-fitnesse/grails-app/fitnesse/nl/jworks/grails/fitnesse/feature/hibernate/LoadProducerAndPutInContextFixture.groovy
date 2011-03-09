package nl.jworks.grails.fitnesse.feature.hibernate

class LoadProducerAndPutInContextFixture {

    public LoadProducerAndPutInContextFixture(String name) {
        CarProducer producer = CarProducer.findByName(name)
        CarProducerContext.instance.producer = producer
    }

}

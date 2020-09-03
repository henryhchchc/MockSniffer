def baseline1(entry):
    if entry['JDK']:
        return False
    if entry['INT']:
        return True
    if entry['ICB']:
        return True
    return False
